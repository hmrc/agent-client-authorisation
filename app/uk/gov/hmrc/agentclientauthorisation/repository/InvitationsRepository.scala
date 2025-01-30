/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation.repository

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mongodb.scala.bson.BsonDocument
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model._
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.RelationshipsConnector
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatus.unapply
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId, Service}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.temporal.ChronoUnit.DAYS
import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import javax.inject._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

@ImplementedBy(classOf[InvitationsRepositoryImpl])
trait InvitationsRepository {
  def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    origin: Option[String]
  ): Future[Invitation]

  def update(invitation: Invitation, status: InvitationStatus, updateDate: LocalDateTime): Future[Invitation]

  def setRelationshipEnded(invitation: Invitation, endedBy: String): Future[Invitation]

  def findByInvitationId(invitationId: InvitationId): Future[Option[Invitation]]

  def findLatestInvitationByClientId(clientId: String, within30Days: Boolean): Future[Option[Invitation]]

  def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty[Service],
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None,
    within30Days: Boolean
  ): Future[List[Invitation]]

  def findInvitationInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service] = None,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None,
    within30Days: Boolean
  ): Future[List[InvitationInfo]]

  def findInvitationInfoBy(
    arn: Arn,
    clientIdTypeAndValues: Seq[(String, String, String)],
    status: Option[InvitationStatus]
  ): Future[List[InvitationInfo]]

  def removePersonalDetails(startDate: LocalDateTime): Future[Unit]

  def removeAllInvitationsForAgent(arn: Arn): Future[Int]

  def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId): Future[Option[Invitation]]

  def removeInvitationById(invitationId: InvitationId): Future[Int]

  def countRemainingPartialAuth(): Future[Long]

  def migratePartialAuthToAcr(rate: Int = 10)(implicit hc: HeaderCarrier): Future[Done]
}

@Singleton
class InvitationsRepositoryImpl @Inject() (
  mongo: MongoComponent,
  metrics: Metrics,
  acrConnector: RelationshipsConnector,
  lockClient: LockClient
)(implicit ec: ExecutionContext, mat: Materializer, appConfig: AppConfig)
    extends PlayMongoRepository[Invitation](
      mongoComponent = mongo,
      collectionName = "invitations",
      domainFormat = InvitationRecordFormat.invitationFormat,
      indexes = Seq(
        IndexModel(ascending(InvitationRecordFormat.invitationIdKey), IndexOptions().name("invitationIdIndex").unique(true).sparse(true)),
        IndexModel(ascending(InvitationRecordFormat.statusKey), IndexOptions().name("findInvitationsByStatusIndex")),
        IndexModel(ascending(InvitationRecordFormat.arnClientStateKey)),
        IndexModel(ascending(InvitationRecordFormat.arnClientServiceStateKey)),
        IndexModel(ascending(InvitationRecordFormat.arnClientServiceStateKey, InvitationRecordFormat.createdKey)),
        IndexModel(ascending(InvitationRecordFormat.createdKey)),
        IndexModel(
          ascending(InvitationRecordFormat.arnKey, InvitationRecordFormat.serviceKey, InvitationRecordFormat.suppliedClientIdKey),
          IndexOptions().partialFilterExpression(BsonDocument(InvitationRecordFormat.statusKey -> "Pending")).unique(true)
        )
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(InvitationId.idFormats),
        Codecs.playFormatCodec(Format(Arn.arnReads, Arn.arnWrites)),
        Codecs.playFormatCodec(StatusChangeEvent.statusChangeEventFormat),
        Codecs.playFormatCodec(MongoLocalDateTimeFormat.localDateTimeFormat)
      ),
      replaceIndexes = false
    ) with InvitationsRepository with Logging with Monitor {

  final val ID = "_id"

  override lazy val requiresTtlIndex: Boolean = false

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  override def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    origin: Option[String]
  ): Future[Invitation] =
    monitor(s"InvitationsRepository-create") {
      val startDate = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime
      val expiryDate = startDate.plusSeconds(appConfig.invitationExpiringDuration.toSeconds).toLocalDate
      val invitation = Invitation.createNew(arn, clientType, service, clientId, suppliedClientId, detailsForEmail, startDate, expiryDate, origin)
      collection
        .insertOne(invitation)
        .toFuture()
        .map(_ => invitation)
    }

  override def update(invitation: Invitation, status: InvitationStatus, updateDate: LocalDateTime): Future[Invitation] =
    monitor(s"InvitationsRepository-update") {
      for {
        invitationOpt <- collection.find(equal(ID, invitation._id)).headOption()
        modifiedOpt = invitationOpt.map(i => i.copy(events = i.events :+ StatusChangeEvent(updateDate, status)))
        updated <- modifiedOpt match {
                     case Some(modified) =>
                       collection.replaceOne(equal(ID, invitation._id), modified).toFuture().flatMap { updateResult =>
                         if (updateResult.getModifiedCount != 1L)
                           Future.failed(new Exception(s"Invitation ${invitation.invitationId.value} update to the new status $status has failed"))
                         else
                           collection
                             .find(equal(ID, invitation._id))
                             .headOption()
                             .map(_.getOrElse(throw new Exception(s"Invitation ${invitation.invitationId.value} not found")))
                       }
                     case None => throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
                   }
      } yield updated
    }

  override def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId): Future[Option[Invitation]] =
    monitor(s"InvitationsRepository-replaceNinoWithMtdItIdFor") {
      collection
        .find(equal(ID, invitation._id))
        .headOption()
        .flatMap {
          case Some(invitation) =>
            val updated = invitation.copy(clientId = mtdItId)
            collection
              .replaceOne(equal(ID, invitation._id), updated)
              .toFuture()
              .map(replaceResult =>
                if (!replaceResult.wasAcknowledged())
                  throw new Exception(s"Invitation ${invitation.invitationId.value} replace Nino with MTDITID has failed.")
                else Option(updated)
              )
          case None => throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
        }
    }

  override def setRelationshipEnded(invitation: Invitation, endedBy: String): Future[Invitation] =
    monitor(s"InvitationsRepository-setRelationshipEnded") {
      for {
        invitationOpt <- collection.find(equal(ID, invitation._id)).headOption()
        modifiedOpt = invitationOpt.map { i =>
                        i.copy(
                          isRelationshipEnded = true,
                          relationshipEndedBy = Some(endedBy),
                          events = i.events :+ StatusChangeEvent(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime, DeAuthorised)
                        )
                      }
        updated <- modifiedOpt match {
                     case Some(modified) =>
                       collection.replaceOne(equal(ID, invitation._id), modified).toFuture().flatMap { updateResult =>
                         if (updateResult.getModifiedCount != 1L)
                           Future.failed(new Exception(s"Invitation ${invitation.invitationId.value} de-authorisation failed"))
                         else
                           collection
                             .find(equal(ID, invitation._id))
                             .headOption()
                             .map(_.getOrElse(throw new Exception(s"Error de-authorising: invitation ${invitation.invitationId.value} not found")))
                       }
                     case None => throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
                   }
      } yield updated
    }

  override def findByInvitationId(invitationId: InvitationId): Future[Option[Invitation]] =
    monitor(s"InvitationsRepository-findByInvitationId") {
      collection
        .find(equal(InvitationRecordFormat.invitationIdKey, invitationId))
        .headOption()
    }

  override def findLatestInvitationByClientId(clientId: String, within30Days: Boolean): Future[Option[Invitation]] =
    monitor(s"InvitationsRepository-findLatestInvitationByClientId") {
      val searchKey = InvitationRecordFormat.toArnClientServiceStateKey(None, clientId = Some(clientId), None, None)
      val query = if (within30Days) {
        and(
          equal(InvitationRecordFormat.arnClientServiceStateKey, searchKey),
          gte(InvitationRecordFormat.createdKey, Instant.now().minus(30, DAYS).toEpochMilli)
        )
      } else {
        equal(InvitationRecordFormat.arnClientServiceStateKey, searchKey)
      }

      collection
        .find(query)
        .sort(descending("events.time"))
        .headOption()
    }

  override def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None,
    within30Days: Boolean
  ): Future[List[Invitation]] =
    monitor(
      s"InvitationsRepository-findInvitationsBy" +
        s"${if (arn.nonEmpty) "-arn" else ""}${if (services.nonEmpty) "-services" else ""}" +
        s"${if (clientId.nonEmpty) "-clientId" else ""}${if (status.nonEmpty) "-status" else ""}" +
        s"${if (createdOnOrAfter.nonEmpty) "-createdOnOrAfter" else ""}"
    ) {

      val createKeys: Seq[String] =
        if (services.length > 1)
          services.map(service => InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, Some(service), status))
        else Seq(InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, services.headOption, status))

      val serviceQuery = createKeys.map(equal(InvitationRecordFormat.arnClientServiceStateKey, _))

      val dateQuery = if (within30Days) {
        Some(gte(InvitationRecordFormat.createdKey, Instant.now().minus(30, DAYS).toEpochMilli))
      } else {
        createdOnOrAfter.map(date => gte(InvitationRecordFormat.createdKey, Instant.from(date.atStartOfDay().atZone(ZoneOffset.UTC)).toEpochMilli))
      }

      val query = dateQuery.fold(or(serviceQuery: _*))(dateQ => and(or(serviceQuery: _*), dateQ))

      collection
        .find(query)
        .sort(descending(InvitationRecordFormat.createdKey))
        .toFuture()
        .map(_.toList)
    }

  override def findInvitationInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service] = None,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None,
    within30Days: Boolean
  ): Future[List[InvitationInfo]] =
    monitor(
      s"InvitationsRepository-findInvitationInfoBy${if (arn.nonEmpty) "-arn" else ""}" +
        s"${if (service.nonEmpty) "-service" else ""}${if (clientId.nonEmpty) "-clientId" else ""}" +
        s"${if (status.nonEmpty) "-status" else ""}${if (createdOnOrAfter.nonEmpty) "-createdOnOrAfter" else ""}"
    ) {
      findInvitationsBy(arn, service.toSeq, clientId, status, createdOnOrAfter, within30Days)
        .map(_.map(Invitation.toInvitationInfo))
    }

  override def findInvitationInfoBy(
    arn: Arn,
    clientIdTypeAndValues: Seq[(String, String, String)],
    status: Option[InvitationStatus]
  ): Future[List[InvitationInfo]] =
    monitor(
      s"InvitationsRepository-findInvitationInfoBy${if (arn.value.nonEmpty) "-arn" else ""}" +
        s"${if (clientIdTypeAndValues.nonEmpty) "-clientIdTypeAndValues" else ""}" +
        s"${if (status.nonEmpty) "-status" else ""}"
    ) {
      val query = status match {
        case None =>
          val keys = clientIdTypeAndValues.map { case (serviceName, clientIdType, clientIdValue) =>
            InvitationRecordFormat
              .toArnClientKey(arn, clientIdValue, serviceName)
          }
          in(InvitationRecordFormat.arnClientServiceStateKey, keys: _*)
        case Some(status) =>
          val keys = clientIdTypeAndValues.map { case (serviceName, clientIdType, clientIdValue) =>
            InvitationRecordFormat
              .toArnClientStateKey(arn.value, clientIdType, clientIdValue, status.toString)
          }
          in(InvitationRecordFormat.arnClientStateKey, keys: _*)
      }

      collection.find(query).toFuture().map(_.map(Invitation.toInvitationInfo).toList)
    }

  override def removePersonalDetails(earlierThanThis: LocalDateTime): Future[Unit] =
    monitor(s"InvitationsRepository-removePersonalDetails") {
      collection
        .updateMany(
          Filters.lte(InvitationRecordFormat.createdKey, Instant.from(earlierThanThis.atZone(ZoneOffset.UTC)).toEpochMilli),
          Updates.unset(InvitationRecordFormat.detailsForEmailKey)
        )
        .toFuture()
        .map(updateManyResult => logger.info(s"Removed personal details for ${updateManyResult.getModifiedCount} invitations."))
    }

  override def removeAllInvitationsForAgent(arn: Arn): Future[Int] =
    monitor(s"InvitationsRepository-removeAllInvitationsForAgent") {
      collection
        .deleteMany(equal(InvitationRecordFormat.arnKey, arn.value))
        .toFuture()
        .map { deleteManyResult =>
          logger.info(s"Deleted ${deleteManyResult.getDeletedCount} records for provided ARN")
          deleteManyResult.getDeletedCount.toInt
        }
    }

  override def removeInvitationById(invitationId: InvitationId): Future[Int] =
    monitor(s"InvitationsRepository-removeInvitationById") {
      collection
        .deleteOne(equal(InvitationRecordFormat.invitationIdKey, invitationId))
        .toFuture()
        .map { deleteOneResult =>
          logger.info(s"Deleted ${deleteOneResult.getDeletedCount} partialAuth invitation for invitationId ${invitationId.value}")
          deleteOneResult.getDeletedCount.toInt
        }
    }

  override def countRemainingPartialAuth(): Future[Long] =
    collection.countDocuments(equal(InvitationRecordFormat.statusKey, unapply(PartialAuth).getOrElse("Partialauth"))).toFuture()

  override def migratePartialAuthToAcr(rate: Int = 10)(implicit hc: HeaderCarrier): Future[Done] = {
    val observable = collection.find(equal(InvitationRecordFormat.statusKey, unapply(PartialAuth).getOrElse("Partialauth")))
    countRemainingPartialAuth().map { count =>
      logger.warn(s"[InvitationsRepository] migrating started, $count partialAuth records to migrate")
    }
    Source
      .fromPublisher(observable)
      .throttle(rate, 1.second)
      .runForeach { record =>
        for {
          migrateResult <- acrConnector.migratePartialAuthRecordToAcr(record)
        } yield migrateResult match {
          case Some("OK") =>
            logger.warn(s"[InvitationsRepository] successfully migrated record with invitationId ${record.invitationId.value}")
            removeInvitationById(record.invitationId).map(_ => ())
          case _ =>
            logger.warn(s"[InvitationsRepository] failed to migrate record with invitationId ${record.invitationId.value}")
        }
      }
      .recover { case _: Throwable =>
        logger.warn("[InvitationsRepository] failed to read partialAuth record before migrating, aborting process")
        Done
      }
      .map { _ =>
        countRemainingPartialAuth().map { count =>
          logger.warn(s"[InvitationsRepository] migration completed, $count partialAuth records left to migrate")
        }
        Done
      }
  }

  if (appConfig.acrPartialAuthMigrate) {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.warn("[InvitationsRepository] migration of partialAuth is starting...")
    lockClient.migrateWithLock(body = migratePartialAuthToAcr())
  } else {
    countRemainingPartialAuth().map { count =>
      logger.warn(s"[InvitationsRepository] migration  of partialAuth is disabled, $count partialAuth records left to migrate")
    }
  }

}
