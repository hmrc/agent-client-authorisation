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

import javax.inject._
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.json.JodaReads._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service.HMRCMTDIT
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

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
    startDate: DateTime,
    expiryDate: LocalDate,
    origin: Option[String])(implicit ec: ExecutionContext): Future[Invitation]

  def update(invitation: Invitation, status: InvitationStatus, updateDate: DateTime)(implicit ec: ExecutionContext): Future[Invitation]

  def setRelationshipEnded(invitation: Invitation, endedBy: String)(implicit ec: ExecutionContext): Future[Invitation]

  def findByInvitationId(invitationId: InvitationId)(implicit ec: ExecutionContext): Future[Option[Invitation]]

  def findLatestInvitationByClientId(clientId: String)(implicit ec: ExecutionContext): Future[Option[Invitation]]

  def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty[Service],
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None)(implicit ec: ExecutionContext): Future[List[Invitation]]

  def findInvitationInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service] = None,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None)(implicit ec: ExecutionContext): Future[List[InvitationInfo]]

  def findInvitationInfoBy(arn: Arn, clientIdTypeAndValues: Seq[(String, String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]]

  def refreshAllInvitations(implicit ec: ExecutionContext): Future[Unit]

  def refreshInvitation(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit]

  def removePersonalDetails(startDate: DateTime)(implicit ec: ExecutionContext): Future[Unit]

  def removeAllInvitationsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int]

  def getExpiredInvitationsForGA(expiredWithin: Long)(implicit ec: ExecutionContext): Future[List[Invitation]]

  def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId)(implicit ec: ExecutionContext): Future[Invitation]
}

@Singleton
class InvitationsRepositoryImpl @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Invitation, BSONObjectID](
      "invitations",
      mongo.mongoConnector.db,
      InvitationRecordFormat.mongoFormat,
      ReactiveMongoFormats.objectIdFormats) with InvitationsRepository with StrictlyEnsureIndexes[Invitation, BSONObjectID] {

  import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper

  final val ID = "_id"

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("invitationId" -> IndexType.Ascending), name = Some("invitationIdIndex"), unique = true, sparse = true),
      Index(Seq(InvitationRecordFormat.arnClientStateKey        -> IndexType.Ascending)),
      Index(Seq(InvitationRecordFormat.arnClientServiceStateKey -> IndexType.Ascending)),
      Index(Seq(InvitationRecordFormat.arnClientServiceStateKey -> IndexType.Ascending, InvitationRecordFormat.createdKey -> IndexType.Ascending))
    )

  def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: DateTime,
    expiryDate: LocalDate,
    origin: Option[String])(implicit ec: ExecutionContext): Future[Invitation] = {
    val invitation = Invitation.createNew(arn, clientType, service, clientId, suppliedClientId, detailsForEmail, startDate, expiryDate, origin)
    insert(invitation).map(_ => invitation)
  }

  def update(invitation: Invitation, status: InvitationStatus, updateDate: DateTime)(implicit ec: ExecutionContext): Future[Invitation] =
    for {
      invitationOpt <- findById(invitation.id)
      modifiedOpt = invitationOpt.map(i => i.copy(events = i.events :+ StatusChangeEvent(updateDate, status)))
      updated <- modifiedOpt match {
                  case Some(modified) =>
                    collection
                      .update(ordered = false)
                      .one(BSONDocument(ID -> invitation.id), bsonJson(modified))
                      .map { result =>
                        if (result.ok) modified
                        else
                          throw new Exception(s"Invitation ${invitation.invitationId.value} update to the new status $status has failed")
                      }
                  case None =>
                    throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
                }
    } yield updated

  def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId)(implicit ec: ExecutionContext): Future[Invitation] =
    for {
      invitationOpt <- findById(invitation.id)
      modifiedOpt = invitationOpt.map(i => i.copy(clientId = mtdItId))
      updated <- modifiedOpt match {
                  case Some(modified) =>
                    collection
                      .update(ordered = false)
                      .one(BSONDocument(ID -> invitation.id), bsonJson(modified))
                      .map { result =>
                        if (result.ok) modified
                        else throw new Exception(s"Invitation ${invitation.invitationId.value} update alt-itsa clientId has failed")
                      }
                  case None => throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
                }
    } yield updated

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]): BSONDocument =
    BSONDocumentFormat.reads(writes.writes(entity)).get

  def setRelationshipEnded(invitation: Invitation, endedBy: String)(implicit ec: ExecutionContext): Future[Invitation] =
    for {
      invitationOpt <- findById(invitation.id)
      modifiedOpt = invitationOpt.map(
        i =>
          i.copy(
            events = i.events :+ StatusChangeEvent(DateTime.now(DateTimeZone.UTC), DeAuthorised),
            isRelationshipEnded = true,
            relationshipEndedBy = Some(endedBy)))
      updated <- modifiedOpt match {
                  case Some(modified) =>
                    collection
                      .update(ordered = false)
                      .one(BSONDocument(ID -> invitation.id), bsonJson(modified))
                      .map { result =>
                        if (result.ok) modified
                        else
                          throw new Exception(s"Invitation ${invitation.invitationId.value} update to deauthorised has failed")
                      }
                  case None =>
                    throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
                }
    } yield updated

  def findByInvitationId(invitationId: InvitationId)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    find("invitationId" -> invitationId).map(_.headOption)

  def findLatestInvitationByClientId(clientId: String)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    collection
      .find[JsObject, JsObject](Json.obj("clientId" -> clientId), None)
      .sort(Json.obj("events.time" -> JsNumber(-1)))
      .cursor[Invitation](ReadPreference.primaryPreferred)
      .headOption

  def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None)(implicit ec: ExecutionContext): Future[List[Invitation]] = {

    val createKeys: Seq[String] =
      if (services.length > 1)
        services.map(service => InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, Some(service), status))
      else Seq(InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, services.headOption, status))

    val serviceQuery: (String, Option[JsValue]) = "$or" -> Some(
      JsArray(createKeys.map(key => Json.obj(InvitationRecordFormat.arnClientServiceStateKey -> Some(JsString(key))))))

    val dateQuery: (String, Option[JsValue]) = InvitationRecordFormat.createdKey -> createdOnOrAfter.map(date =>
      Json.obj("$gte" -> JsNumber(date.toDateTimeAtStartOfDay().getMillis)))

    val searchOptions: Seq[(String, JsValueWrapper)] =
      Seq(serviceQuery, dateQuery)
        .filter(_._2.isDefined)
        .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    val query =
      if (searchOptions.nonEmpty) Json.obj("$and" -> searchOptions.map(so => Json.obj(so._1 -> so._2)))
      else Json.obj("$and"                        -> Json.parse("[{}]"))

    collection
      .find[JsObject, JsObject](query, None)
      .sort(Json.obj(InvitationRecordFormat.createdKey -> JsNumber(-1)))
      .cursor[Invitation](ReadPreference.primaryPreferred)
      .collect[List](-1, Cursor.FailOnError[List[Invitation]]())
  }

  def findInvitationInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service] = None,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None)(implicit ec: ExecutionContext): Future[List[InvitationInfo]] = {

    val key = InvitationRecordFormat.toArnClientServiceStateKey(arn, clientId, service, status)
    val searchOptions: Seq[(String, JsValueWrapper)] = Seq(
      InvitationRecordFormat.arnClientServiceStateKey -> Some(JsString(key)),
      InvitationRecordFormat.createdKey               -> createdOnOrAfter.map(date => Json.obj("$gte" -> JsNumber(date.toDateTimeAtStartOfDay().getMillis)))
    ).filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    val query = Json.obj(searchOptions: _*)
    findInvitationInfoBySearch(query)
  }

  private def findInvitationInfoBySearch(query: JsObject)(implicit ec: ExecutionContext): Future[List[InvitationInfo]] =
    collection
      .find(
        query,
        Some(Json.obj(
          "invitationId"                   -> 1,
          "expiryDate"                     -> 1,
          InvitationRecordFormat.statusKey -> 1,
          "arn"                            -> 1,
          "service"                        -> 1,
          "isRelationshipEnded"            -> 1,
          "events"                         -> 1,
          "clientId"                       -> 1,
          "suppliedClientId"               -> 1
        ))
      )
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List](1000, Cursor.FailOnError[List[JsObject]]())
      .map(
        _.map(
          (x: JsValue) =>
            (
              x \ "invitationId",
              x \ "expiryDate",
              x \ InvitationRecordFormat.statusKey,
              x \ "arn",
              x \ "service",
              (x \ "isRelationshipEnded").orElse(JsDefined(JsBoolean(false))),
              (x \ "relationshipEndedBy").orElse(JsDefined(JsNull)),
              x \ "events",
              x \ "clientId",
              x \ "suppliedClientId"))
          .map(properties =>
            InvitationInfo(
              properties._1.as[InvitationId],
              properties._2.as[LocalDate],
              properties._3.as[InvitationStatus],
              properties._4.as[Arn],
              properties._5.as[Service],
              properties._6.as[Boolean],
              properties._7.asOpt[String],
              properties._8.as[List[StatusChangeEvent]],
              (properties._5.as[Service].id == HMRCMTDIT && properties._9.as[String] == properties._10.as[String]) || properties._3
                .as[InvitationStatus] == PartialAuth
          )))

  def findInvitationInfoBy(arn: Arn, clientIdTypeAndValues: Seq[(String, String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]] = {

    val query = status match {
      case None => {
        val keys = clientIdTypeAndValues.map {
          case (serviceName, clientIdType, clientIdValue) =>
            InvitationRecordFormat
              .toArnClientKey(arn, clientIdValue, serviceName)
        }
        Json.obj(InvitationRecordFormat.arnClientServiceStateKey -> Json.obj("$in" -> keys))
      }
      case Some(status) => {
        val keys = clientIdTypeAndValues.map {
          case (serviceName, clientIdType, clientIdValue) =>
            InvitationRecordFormat
              .toArnClientStateKey(arn.value, clientIdType, clientIdValue, status.toString)
        }
        Json.obj(InvitationRecordFormat.arnClientStateKey -> Json.obj("$in" -> keys))
      }
    }

    findInvitationInfoBySearch(query)
  }

  def refreshAllInvitations(implicit ec: ExecutionContext): Future[Unit] =
    for {
      ids <- collection
              .find[JsObject, JsObject](Json.obj(), None)
              .cursor[JsObject]()
              .collect[List](100, Cursor.FailOnError[List[JsObject]]())
      _ <- Future.sequence(ids.map(json => (json \ ID).as[BSONObjectID]).map(refreshInvitation))
    } yield ()

  def refreshInvitation(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit] =
    for {
      invitationOpt <- findById(id)
      _ <- invitationOpt match {
            case Some(invitation) =>
              collection.update(ordered = false).one(BSONDocument(ID -> id), bsonJson(invitation)).map(_ => ())
            case None => Future.successful(())
          }
    } yield ()

  def removePersonalDetails(earlierThanThis: DateTime)(implicit ec: ExecutionContext): Future[Unit] = {
    val query = Json.obj(
      InvitationRecordFormat.createdKey ->
        Json.obj("$lte" -> JsNumber(earlierThanThis.getMillis)))
    val update = Json.obj("$set" -> Json.obj(InvitationRecordFormat.detailsForEmailKey -> JsNull))
    val updateColl = collection.update(true)
    val updates = updateColl.element(query, update, false, true)
    updates.map(op => updateColl.many(Iterable(op))).map(_ => ())
  }

  override def removeAllInvitationsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int] = {
    val query = Json.obj("arn" -> arn.value)
    collection.delete().one(query).map(_.n)
  }

  override def getExpiredInvitationsForGA(expiredWithin: Long)(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val query =
      Json.obj("events.status" -> JsString("Expired"), "events.time" -> Json.obj("$gte" -> JsNumber(DateTime.now().getMillis - expiredWithin)))
    collection
      .find[JsObject, JsObject](query, None)
      .sort(Json.obj(InvitationRecordFormat.createdKey -> JsNumber(-1)))
      .cursor[Invitation](ReadPreference.primaryPreferred)
      .collect[List](-1, Cursor.FailOnError[List[Invitation]]())
  }
}
