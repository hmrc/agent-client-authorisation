/*
 * Copyright 2020 HM Revenue & Customs
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
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JodaReads._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.api.{Cursor, ReadPreference}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
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
    expiryDate: LocalDate)(implicit ec: ExecutionContext): Future[Invitation]

  def update(invitation: Invitation, status: InvitationStatus, updateDate: DateTime)(
    implicit ec: ExecutionContext): Future[Invitation]

  def findByInvitationId(invitationId: InvitationId)(implicit ec: ExecutionContext): Future[Option[Invitation]]

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

  def findInvitationInfoBy(arn: Arn, clientIdTypeAndValues: Seq[(String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]]

  def refreshAllInvitations(implicit ec: ExecutionContext): Future[Unit]

  def refreshInvitation(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit]

  def removeEmailDetails(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit]

  def removeAllInvitationsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int]
}

@Singleton
class InvitationsRepositoryImpl @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Invitation, BSONObjectID](
      "invitations",
      mongo.mongoConnector.db,
      InvitationRecordFormat.mongoFormat,
      ReactiveMongoFormats.objectIdFormats) with InvitationsRepository
    with StrictlyEnsureIndexes[Invitation, BSONObjectID] {

  import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper

  final val ID = "_id"

  override def indexes: Seq[Index] =
    Seq(
      Index(
        key = Seq("invitationId" -> IndexType.Ascending),
        name = Some("invitationIdIndex"),
        unique = true,
        sparse = true),
      Index(Seq(InvitationRecordFormat.arnClientStateKey        -> IndexType.Ascending)),
      Index(Seq(InvitationRecordFormat.arnClientServiceStateKey -> IndexType.Ascending)),
      Index(
        Seq(
          InvitationRecordFormat.arnClientServiceStateKey -> IndexType.Ascending,
          InvitationRecordFormat.createdKey               -> IndexType.Ascending))
    )

  def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: DateTime,
    expiryDate: LocalDate)(implicit ec: ExecutionContext): Future[Invitation] = {
    val invitation =
      Invitation.createNew(arn, clientType, service, clientId, suppliedClientId, detailsForEmail, startDate, expiryDate)
    insert(invitation).map(_ => invitation)
  }

  def update(invitation: Invitation, status: InvitationStatus, updateDate: DateTime)(
    implicit ec: ExecutionContext): Future[Invitation] =
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
                          throw new Exception(
                            s"Invitation ${invitation.invitationId.value} update to the new status $status has failed")
                      }
                  case None =>
                    throw new Exception(s"Invitation ${invitation.invitationId.value} not found")
                }
    } yield updated

  def findByInvitationId(invitationId: InvitationId)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    find("invitationId" -> invitationId).map(_.headOption)

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
      .collect[List](1000, Cursor.FailOnError[List[Invitation]]())
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
      InvitationRecordFormat.createdKey -> createdOnOrAfter.map(date =>
        Json.obj("$gte" -> JsNumber(date.toDateTimeAtStartOfDay().getMillis)))
    ).filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    val query = Json.obj(searchOptions: _*)
    findInvitationInfoBySearch(query)
  }

  def findInvitationInfoBy(arn: Arn, clientIdTypeAndValues: Seq[(String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]] = {

    val keys = clientIdTypeAndValues.map {
      case (clientIdType, clientIdValue) =>
        InvitationRecordFormat
          .toArnClientStateKey(arn.value, clientIdType, clientIdValue, status.getOrElse("").toString)
    }
    val query = Json.obj(InvitationRecordFormat.arnClientStateKey -> Json.obj("$in" -> keys))
    findInvitationInfoBySearch(query)
  }

  private def findInvitationInfoBySearch(query: JsObject)(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]] = {

    implicit val domainFormatImplicit: Format[Invitation] = InvitationRecordFormat.mongoFormat
    implicit val idFormatImplicit: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats

    collection
      .find(
        query,
        Some(
          Json.obj(
            "invitationId"                   -> 1,
            "expiryDate"                     -> 1,
            InvitationRecordFormat.statusKey -> 1,
            "arn"                            -> 1,
            "service"                        -> 1)))
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List](1000, Cursor.FailOnError[List[JsObject]]())
      .map(
        _.map((x: JsValue) =>
          (x \ "invitationId", x \ "expiryDate", x \ InvitationRecordFormat.statusKey, x \ "arn", x \ "service"))
          .map(
            properties =>
              InvitationInfo(
                properties._1.as[InvitationId],
                properties._2.as[LocalDate],
                properties._3.as[InvitationStatus],
                properties._4.as[Arn],
                properties._5.as[Service])))
  }

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]): BSONDocument =
    BSONDocumentFormat.reads(writes.writes(entity)).get

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

  def removeEmailDetails(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit] = {
    val updatedInvitation = invitation.copy(detailsForEmail = None)
    collection.update(ordered = false).one(BSONDocument(ID -> invitation.id), bsonJson(updatedInvitation)).map {
      result =>
        if (result.ok) ()
        else throw new Exception(s"Unable to remove email details from: ${updatedInvitation.invitationId.value}")
    }
  }

  override def removeAllInvitationsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int] = {
    val query = Json.obj("arn" -> arn.value)
    collection.delete().one(query).map(_.n)
  }
}
