/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.libs.json.{Format, Json, Writes}
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.BSONFormats
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsRepository @Inject()(mongo: DB)
  extends ReactiveRepository[Invitation, BSONObjectID]("invitations", () => mongo, DomainFormat.mongoFormat, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[Invitation] {

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("invitationId" -> IndexType.Ascending), name = Some("invitationIdIndex"), unique = true),
    Index(Seq("arn" -> IndexType.Ascending)),
    Index(Seq("clientId" -> IndexType.Ascending)),
    Index(Seq("service" -> IndexType.Ascending))
  )

  def create(arn: Arn, service: Service, clientId: ClientId, suppliedClientId: ClientId,
             postcode: Option[String], startDate: DateTime, expiryDate: LocalDate)
            (implicit ec: ExecutionContext): Future[Invitation] = {

    val invitation = Invitation.createNew(arn, service, clientId, suppliedClientId, postcode, startDate, expiryDate)
    insert(invitation).map(_ => invitation)
  }

  def list(arn: Arn, service: Option[Service], clientId: Option[String], status: Option[InvitationStatus])(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val searchOptions = Seq("arn" -> Some(arn.value),
      "clientId" -> clientId,
      "service" -> service.map(_.id),
      "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'"))

      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  def list(service: Service, clientId: ClientId, status: Option[InvitationStatus])(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val searchOptions = Seq(
      "service" -> Some(service.id),
      "clientId" -> Some(clientId.value),
      statusSearchOption(status)
    )
      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  private def statusSearchOption(status: Option[InvitationStatus]): (String, Option[String]) = {
    "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'")
  }

  def findRegimeID(clientId: String)(implicit ec: ExecutionContext): Future[List[Invitation]] =
    find()


  def update(id: BSONObjectID, status: InvitationStatus, updateDate: DateTime)(implicit ec: ExecutionContext): Future[Invitation] = {
    val update = atomicUpdate(BSONDocument("_id" -> id), BSONDocument("$push" -> BSONDocument("events" -> bsonJson(StatusChangeEvent(updateDate, status)))))
    update.map(_.map(_.updateType.savedValue).get)
  }

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]) = BSONFormats.toBSON(Json.toJson(entity)).get

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: Invitation): Boolean =
    newRecordId != oldRecord.id
}

object DomainFormat {

  import play.api.libs.json.{ JsPath, Reads }
  import play.api.libs.functional.syntax._

  implicit val serviceFormat = Service.format
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats

  def read(id: BSONObjectID, invitationId: InvitationId, arn: Arn, service: Service, clientId: String, clientIdType: String,
           suppliedClientId: String, suppliedClientIdType: String, postcode: Option[String], expiryDateOp: Option[LocalDate],
           events: List[StatusChangeEvent]): Invitation = {
    val expiryDate = expiryDateOp.getOrElse(events.head.time.plusDays(10).toLocalDate)

    Invitation(id, invitationId, arn, service, ClientIdentifier(clientId, clientIdType),
      ClientIdentifier(suppliedClientId, suppliedClientIdType), postcode, expiryDate, events)
  }

  val reads: Reads[Invitation] = (
    (JsPath \ "id").read[BSONObjectID] and
      (JsPath \ "invitationId").read[InvitationId] and
      (JsPath \ "arn").read[Arn] and
      (JsPath \ "service").read[Service] and
      (JsPath \ "clientId").read[String] and
      (JsPath \ "clientIdType").read[String] and
      (JsPath \ "suppliedClientId").read[String] and
      (JsPath \ "suppliedClientIdType").read[String] and
      (JsPath \ "postcode").readNullable[String] and
      (JsPath \ "expiryDate").readNullable[LocalDate] and
      (JsPath \ "events").read[List[StatusChangeEvent]]) (read _)

  val writes  = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "id" -> invitation.id,
      "invitationId" -> invitation.invitationId,
      "arn" -> invitation.arn.value,
      "service" -> invitation.service.id,
      "clientId" -> invitation.clientId.value,
      "clientIdType" -> invitation.clientId.typeId,
      "postcode" -> invitation.postcode,
      "suppliedClientId" -> invitation.suppliedClientId.value,
      "suppliedClientIdType" -> invitation.suppliedClientId.typeId,
      "events" -> invitation.events,
      "expiryDate" -> invitation.expiryDate
    )
  }

  val mongoFormat = ReactiveMongoFormats.mongoEntity(Format(reads, writes))
}
