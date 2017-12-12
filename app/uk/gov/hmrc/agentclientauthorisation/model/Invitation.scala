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

package uk.gov.hmrc.agentclientauthorisation.model

import org.joda.time.DateTime
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.CLIENT_ID_TYPE_NINO
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.{SimpleObjectReads, SimpleObjectWrites}
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

sealed trait InvitationStatus {

  def toEither: Either[String, InvitationStatus] = this match {
    case Unknown(status) => Left(status)
    case status => Right(status)
  }

  def leftMap[X](f: String => X) =
    toEither.left.map(f)
}

case object Pending extends InvitationStatus

case object Expired extends InvitationStatus

case object Rejected extends InvitationStatus

case object Accepted extends InvitationStatus

case object Cancelled extends InvitationStatus

case class Unknown(attempted: String) extends InvitationStatus

object InvitationStatus {
  def unapply(status: InvitationStatus): Option[String] = status match {
    case Pending => Some("Pending")
    case Rejected => Some("Rejected")
    case Accepted => Some("Accepted")
    case Cancelled => Some("Cancelled")
    case Expired => Some("Expired")
    case _ => None
  }

  def apply(status: String): InvitationStatus = status.toLowerCase match {
    case "pending" => Pending
    case "rejected" => Rejected
    case "accepted" => Accepted
    case "cancelled" => Cancelled
    case "expired" => Expired
    case _ => Unknown(status)
  }

  implicit val invitationStatusFormat = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = apply(json.as[String]) match {
      case Unknown(value) => JsError(s"Status of [$value] is not a valid InvitationStatus")
      case value => JsSuccess(value)
    }

    override def writes(o: InvitationStatus): JsValue = unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }
}

case class StatusChangeEvent(time: DateTime, status: InvitationStatus)

case class ClientIdMapping(id: BSONObjectID,
                           canonicalClientId: String,
                           canonicalClientIdType: String,
                           suppliedClientId: String,
                           suppliedClientIdType: String)

object ClientIdMapping {
  implicit val dateWrites = RestFormats.dateTimeWrite
  implicit val dateReads = RestFormats.dateTimeRead
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "canonicalClientId" -> invitation.clientIdType,
      "canonicalClientIdType" -> invitation.clientId,
      "suppliedClientId" -> invitation.suppliedClientId,
      "suppliedClientIdType" -> invitation.suppliedClientIdType,
      "created" -> invitation.firstEvent().time,
      "lastUpdated" -> invitation.mostRecentEvent().time
    )

  }

  val mongoFormats = ReactiveMongoFormats.mongoEntity(Json.format[ClientIdMapping])
}

// TODO consider revisting this type that was commented out earlier so that we can use our own domain model type rather
// TODO than TaxIdentifier from the domain library which is implemented by many types that we don't support
//object ClientId {
//  sealed abstract class Type
//  case object MtdItId extends Type
//  case object Nino extends Type
//}
//
//case class ClientId(val idType: ClientId.Type, val value: String)

case class Invitation(
                       id: BSONObjectID,
                       invitationId: InvitationId,
                       arn: Arn,
                       service: Service,
                       clientId: String,
                       postcode: String,
                       suppliedClientId: String,
                       suppliedClientIdType: String,
                       events: List[StatusChangeEvent]) {

  // TODO investigate why this was set to ni and is it correct, seems strange
  val clientIdType = CLIENT_ID_TYPE_NINO

  def firstEvent(): StatusChangeEvent = {
    events.head
  }

  def mostRecentEvent(): StatusChangeEvent = {
    events.last
  }

  def status = mostRecentEvent().status
}

/** Information provided by the agent to offer representation to HMRC */
case class AgentInvitation(
                            service: String,
                            clientIdType: String,
                            clientId: String,
                            clientPostcode: String)

object StatusChangeEvent {
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
}

sealed class Service(val id: String, val invitationIdPrefix: Char) {

  override def equals(that: Any): Boolean =
    that match {
      case that: Service => this.id.equals(that.id)
      case _ => false
  }
}

object Service {
  case object MtdIt extends Service("HMRC-MTD-IT", 'A')
  case object PersonalIncomeRecord extends Service("PERSONAL-INCOME-RECORD", 'B')
  val values = Seq(MtdIt, PersonalIncomeRecord)
  def findById(id: String): Option[Service] = values.filter(_.id == id).headOption
  def forId(id: String): Service = findById(id).getOrElse(throw new Exception("Not a valid service"))

  def apply(id: String) = forId(id)
  def unapply(service: Service): Option[String] = Some(service.id)

  val reads = new SimpleObjectReads[Service]("id", Service.apply)
  val writes = new SimpleObjectWrites[Service](_.id)
  val format = Format(reads, writes)
}

object Invitation {

  implicit val dateWrites = RestFormats.dateTimeWrite
  implicit val dateReads = RestFormats.dateTimeRead
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "service" -> invitation.service.id,
      "clientIdType" -> invitation.clientIdType,
      "clientId" -> invitation.clientId,
      "postcode" -> invitation.postcode,
      "arn" -> invitation.arn.value,
      "suppliedClientId" -> invitation.suppliedClientId,
      "suppliedClientIdType" -> invitation.suppliedClientIdType,
      "created" -> invitation.firstEvent().time,
      "lastUpdated" -> invitation.mostRecentEvent().time,
      "status" -> invitation.status
    )

  }

  implicit val serviceFormat = Service.format
  val mongoFormats = ReactiveMongoFormats.mongoEntity(Json.format[Invitation])
}

object AgentInvitation {
  implicit val format = Json.format[AgentInvitation]
}