/*
 * Copyright 2018 HM Revenue & Customs
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

import javax.inject._
import org.joda.time.{DateTime, LocalDate}
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
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Invitation, BSONObjectID](
      "invitations",
      mongo.mongoConnector.db,
      InvitationRecordFormat.mongoFormat,
      ReactiveMongoFormats.objectIdFormats) with AtomicUpdate[Invitation]
    with StrictlyEnsureIndexes[Invitation, BSONObjectID] {

  import ImplicitBSONHandlers._
  import play.api.libs.json.Json.JsValueWrapper

  override def indexes: Seq[Index] =
    Seq(
      Index(
        key = Seq("invitationId" -> IndexType.Ascending),
        name = Some("invitationIdIndex"),
        unique = true,
        sparse = true),
      Index(Seq("arn"                                           -> IndexType.Ascending)),
      Index(Seq("clientId"                                      -> IndexType.Ascending)),
      Index(Seq("service"                                       -> IndexType.Ascending)),
      Index(Seq(InvitationRecordFormat.arnClientStateKey        -> IndexType.Ascending)),
      Index(Seq(InvitationRecordFormat.arnClientServiceStateKey -> IndexType.Ascending))
    )

  def create(
    arn: Arn,
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    startDate: DateTime,
    expiryDate: LocalDate)(implicit ec: ExecutionContext): Future[Invitation] = {

    val invitation = Invitation.createNew(arn, service, clientId, suppliedClientId, startDate, expiryDate)
    insert(invitation).map(_ => invitation)
  }

  def findSorted(sortBy: JsObject, query: (String, JsValueWrapper)*)(
    implicit ec: ExecutionContext): Future[List[Invitation]] = {
    import ImplicitBSONHandlers._
    implicit val domainFormatImplicit: Format[Invitation] = InvitationRecordFormat.mongoFormat
    implicit val idFormatImplicit: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats

    collection
      .find(Json.obj(query: _*))
      .sort(sortBy)
      .cursor[Invitation](ReadPreference.primaryPreferred)
      .collect[List](100, Cursor.FailOnError[List[Invitation]]())
  }

  def list(
    arn: Arn,
    service: Option[Service],
    clientId: Option[String],
    status: Option[InvitationStatus],
    createdOnOrAfter: Option[LocalDate])(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val key = InvitationRecordFormat.toArnClientServiceStateKey(Some(arn), clientId, service, status)
    val searchOptions: Seq[(String, JsValueWrapper)] = Seq(
      InvitationRecordFormat.arnClientServiceStateKey -> Some(JsString(key)),
      InvitationRecordFormat.createdKey -> createdOnOrAfter.map(date =>
        Json.obj("$gte" -> JsNumber(date.toDateTimeAtStartOfDay().getMillis)))
    ).filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    findSorted(Json.obj(InvitationRecordFormat.createdKey -> JsNumber(-1)), searchOptions: _*)
  }

  def findAllInvitationIdAndExpiryDate(arn: Arn, clientIds: Seq[(String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationIdAndExpiryDate]] = {
    import ImplicitBSONHandlers._
    implicit val domainFormatImplicit: Format[Invitation] = InvitationRecordFormat.mongoFormat
    implicit val idFormatImplicit: Format[BSONObjectID] = ReactiveMongoFormats.objectIdFormats
    val keys = clientIds.map {
      case (clientIdType, clientIdValue) =>
        InvitationRecordFormat
          .toArnClientStateKey(arn.value, clientIdType, clientIdValue, status.getOrElse("").toString)
    }
    val query = Json.obj(InvitationRecordFormat.arnClientStateKey -> Json.obj("$in" -> keys))
    collection
      .find(query, Json.obj("invitationId" -> 1, "expiryDate" -> 1))
      .cursor[JsObject](ReadPreference.primaryPreferred)
      .collect[List](100)
      .map(_.map((x: JsValue) => (x \ "invitationId", x \ "expiryDate"))
        .map(lookResults => InvitationIdAndExpiryDate(lookResults._1.as[InvitationId], lookResults._2.as[LocalDate])))
  }

  def list(service: Service, clientId: ClientId, status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val key = InvitationRecordFormat.toArnClientServiceStateKey(None, Some(clientId.value), Some(service), status)
    val searchOptions = Seq(InvitationRecordFormat.arnClientServiceStateKey -> toJsFieldJsValueWrapper(JsString(key)))
    find(searchOptions: _*)
  }

  def findRegimeID(clientId: String)(implicit ec: ExecutionContext): Future[List[Invitation]] =
    find()

  def update(invitation: Invitation, status: InvitationStatus, updateDate: DateTime)(
    implicit ec: ExecutionContext): Future[Invitation] =
    for {
      invitations <- collection.find(BSONDocument("_id" -> invitation.id)).cursor[Invitation].collect[List]()
      modified = invitations.head.copy(events = invitations.head.events :+ StatusChangeEvent(updateDate, status))
      update <- atomicUpdate(BSONDocument("_id" -> invitation.id), bsonJson(modified))
      saved  <- Future.successful(update.map(_.updateType.savedValue).get)
    } yield saved

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]): BSONDocument =
    BSONDocumentFormat.reads(writes.writes(entity)).get

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: Invitation): Boolean =
    newRecordId != oldRecord.id

  def refreshAllInvitations(implicit ec: ExecutionContext): Future[Unit] =
    for {
      ids <- collection
              .find(Json.obj(), Json.obj())
              .cursor[JsObject]()
              .collect[List]()
      _ <- Future.sequence(ids.map(json => (json \ "_id").as[BSONObjectID]).map(refreshInvitation))
    } yield ()

  def refreshInvitation(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit] =
    for {
      invitation <- collection.find(BSONDocument("_id" -> id)).one[Invitation]
      _          <- atomicUpdate(BSONDocument("_id" -> id), bsonJson(invitation))
    } yield ()
}
