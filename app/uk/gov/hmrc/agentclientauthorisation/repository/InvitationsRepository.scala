/*
 * Copyright 2022 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.{ascending, descending}
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Updates}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId, Service}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import javax.inject._
import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[InvitationsRepositoryImpl])
trait InvitationsRepository {
  def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: LocalDateTime,
    expiryDate: LocalDate,
    origin: Option[String]): Future[Invitation]

  def update(invitation: Invitation, status: InvitationStatus, updateDate: LocalDateTime): Future[Invitation]
  def setRelationshipEnded(invitation: Invitation, endedBy: String): Future[Invitation]
  def findByInvitationId(invitationId: InvitationId): Future[Option[Invitation]]
  def findLatestInvitationByClientId(clientId: String): Future[Option[Invitation]]
  def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty[Service],
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None): Future[List[Invitation]]
  def findInvitationInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service] = None,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None): Future[List[InvitationInfo]]
  def findInvitationInfoBy(
    arn: Arn,
    clientIdTypeAndValues: Seq[(String, String, String)],
    status: Option[InvitationStatus]): Future[List[InvitationInfo]]
  def removePersonalDetails(startDate: LocalDateTime): Future[Unit]
  def removeAllInvitationsForAgent(arn: Arn): Future[Int]
  def getExpiredInvitationsForGA(expiredWithin: Long): Future[List[Invitation]]
  def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId): Future[Invitation]
}

@Singleton
class InvitationsRepositoryImpl @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[Invitation](
      mongoComponent = mongo,
      collectionName = "invitations",
      domainFormat = InvitationRecordFormat.invitationFormat,
      indexes = Seq(
        IndexModel(ascending(InvitationRecordFormat.invitationIdKey), IndexOptions().name("invitationIdIndex").unique(true).sparse(true)),
        IndexModel(ascending(InvitationRecordFormat.arnClientStateKey)),
        IndexModel(ascending(InvitationRecordFormat.arnClientServiceStateKey)),
        IndexModel(ascending(InvitationRecordFormat.arnClientServiceStateKey, InvitationRecordFormat.createdKey)),
        IndexModel(ascending(InvitationRecordFormat.createdKey))
      ),
      extraCodecs = Seq(
        Codecs.playFormatCodec(InvitationId.idFormats),
        Codecs.playFormatCodec(Format(Arn.arnReads, Arn.arnWrites)),
        Codecs.playFormatCodec(StatusChangeEvent.statusChangeEventFormat),
        Codecs.playFormatCodec(MongoLocalDateTimeFormat.localDateTimeFormat)
      )
    ) with InvitationsRepository with Logging {

  final val ID = "_id"

  override def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: LocalDateTime,
    expiryDate: LocalDate,
    origin: Option[String]): Future[Invitation] = {
    val invitation = Invitation.createNew(arn, clientType, service, clientId, suppliedClientId, detailsForEmail, startDate, expiryDate, origin)
    collection
      .insertOne(invitation)
      .toFuture()
      .map(_ => invitation)
  }

  override def update(invitation: Invitation, status: InvitationStatus, updateDate: LocalDateTime): Future[Invitation] =
    for {
      invitationOpt <- collection.find(equal(ID, invitation._id)).headOption()
      modifiedOpt = invitationOpt.map(i => {
        i.copy(events = i.events :+ StatusChangeEvent(updateDate, status))
      })
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

  override def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId): Future[Invitation] =
    collection
      .find(equal(ID, invitation._id))
      .headOption()
      .flatMap {
        case Some(invitation) =>
          val updated = invitation.copy(clientId = mtdItId)
          collection
            .replaceOne(equal(ID, invitation._id), updated)
            .toFuture()
            .map(
              replaceResult =>
                if (!replaceResult.wasAcknowledged())
                  throw new Exception(s"Invitation ${invitation.invitationId.value} replace Nino with MTDITID has failed.")
                else updated)
        case None => throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
      }

  override def setRelationshipEnded(invitation: Invitation, endedBy: String): Future[Invitation] =
    for {
      invitationOpt <- collection.find(equal(ID, invitation._id)).headOption()
      modifiedOpt = invitationOpt.map(i => {
        i.copy(
          isRelationshipEnded = true,
          relationshipEndedBy = Some(endedBy),
          events = i.events :+ StatusChangeEvent(Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime, DeAuthorised)
        )
      })
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

  override def findByInvitationId(invitationId: InvitationId): Future[Option[Invitation]] =
    collection
      .find(equal(InvitationRecordFormat.invitationIdKey, invitationId))
      .headOption()

  override def findLatestInvitationByClientId(clientId: String): Future[Option[Invitation]] = {
    val searchKey = InvitationRecordFormat.toArnClientServiceStateKey(None, clientId = Some(clientId), None, None)
    collection
      .find(equal(InvitationRecordFormat.arnClientServiceStateKey, searchKey))
      .sort(descending("events.time"))
      .headOption
  }

  override def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None): Future[List[Invitation]] = {

    val createKeys: Seq[String] =
      if (services.length > 1)
        services.map(service => InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, Some(service), status))
      else Seq(InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, services.headOption, status))

    val serviceQuery = createKeys.map(equal(InvitationRecordFormat.arnClientServiceStateKey, _))

    val dateQuery =
      createdOnOrAfter.map(date => gte(InvitationRecordFormat.createdKey, Instant.from(date.atStartOfDay().atZone(ZoneOffset.UTC)).toEpochMilli))

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
    createdOnOrAfter: Option[LocalDate] = None): Future[List[InvitationInfo]] =
    findInvitationsBy(arn, service.toSeq, clientId, status, createdOnOrAfter)
      .map(_.map(Invitation.toInvitationInfo))

  override def findInvitationInfoBy(
    arn: Arn,
    clientIdTypeAndValues: Seq[(String, String, String)],
    status: Option[InvitationStatus]): Future[List[InvitationInfo]] = {

    val query = status match {
      case None => {
        val keys = clientIdTypeAndValues.map {
          case (serviceName, clientIdType, clientIdValue) =>
            InvitationRecordFormat
              .toArnClientKey(arn, clientIdValue, serviceName)
        }
        in(InvitationRecordFormat.arnClientServiceStateKey, keys: _*)
      }
      case Some(status) => {
        val keys = clientIdTypeAndValues.map {
          case (serviceName, clientIdType, clientIdValue) =>
            InvitationRecordFormat
              .toArnClientStateKey(arn.value, clientIdType, clientIdValue, status.toString)
        }
        in(InvitationRecordFormat.arnClientStateKey, keys: _*)
      }
    }

    collection.find(query).toFuture().map(_.map(Invitation.toInvitationInfo).toList)
  }

  override def removePersonalDetails(earlierThanThis: LocalDateTime): Future[Unit] =
    collection
      .updateMany(
        Filters.lte(InvitationRecordFormat.createdKey, Instant.from(earlierThanThis.atZone(ZoneOffset.UTC)).toEpochMilli),
        Updates.unset(InvitationRecordFormat.detailsForEmailKey)
      )
      .toFuture()
      .map(updateManyResult => logger.info(s"Removed personal details for ${updateManyResult.getModifiedCount} invitations."))

  override def removeAllInvitationsForAgent(arn: Arn): Future[Int] =
    collection
      .deleteMany(equal(InvitationRecordFormat.arnKey, arn.value))
      .toFuture()
      .map(deleteManyResult => {
        logger.info(s"Deleted ${deleteManyResult.getDeletedCount} records for provided ARN")
        deleteManyResult.getDeletedCount.toInt
      })

  override def getExpiredInvitationsForGA(expiredWithin: Long): Future[List[Invitation]] =
    collection
      .find(
        and(
          equal("events.status", "Expired"),
          gte("events.time", Instant.from(LocalDateTime.now.minusSeconds(expiredWithin).atZone(ZoneOffset.UTC)).toEpochMilli)))
      .sort(descending(InvitationRecordFormat.createdKey))
      .toFuture()
      .map(_.toList)
}
