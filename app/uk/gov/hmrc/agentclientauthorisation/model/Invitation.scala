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

package uk.gov.hmrc.agentclientauthorisation.model

import org.bson.types.ObjectId
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Service}
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}

sealed trait InvitationStatus {

  def toEither: Either[String, InvitationStatus] = this match {
    case Unknown(status) => Left(status)
    case status          => Right(status)
  }

  def leftMap[X](f: String => X): Either[X, InvitationStatus] =
    toEither.left.map(f)

  override def toString: String = InvitationStatus.unapply(this).getOrElse("Unknown")
}

case object Pending extends InvitationStatus

case object Expired extends InvitationStatus

case object Rejected extends InvitationStatus

case object Accepted extends InvitationStatus

case object Cancelled extends InvitationStatus

case object DeAuthorised extends InvitationStatus

case object PartialAuth extends InvitationStatus

case class Unknown(attempted: String) extends InvitationStatus

object InvitationStatus {
  def unapply(status: InvitationStatus): Option[String] = status match {
    case Pending      => Some("Pending")
    case Rejected     => Some("Rejected")
    case Accepted     => Some("Accepted")
    case Cancelled    => Some("Cancelled")
    case Expired      => Some("Expired")
    case DeAuthorised => Some("Deauthorised")
    case PartialAuth  => Some("Partialauth")
    case _            => None
  }

  def apply(status: String): InvitationStatus = status.toLowerCase match {
    case "pending"      => Pending
    case "rejected"     => Rejected
    case "accepted"     => Accepted
    case "cancelled"    => Cancelled
    case "expired"      => Expired
    case "deauthorised" => DeAuthorised
    case "partialauth"  => PartialAuth
    case _              => Unknown(status)
  }

  implicit val invitationStatusFormat: Format[InvitationStatus] = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = apply(json.as[String]) match {
      case Unknown(value) => JsError(s"Status of [$value] is not a valid InvitationStatus")
      case value          => JsSuccess(value)
    }

    override def writes(o: InvitationStatus): JsValue =
      unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }
}

case class StatusChangeEvent(time: LocalDateTime, status: InvitationStatus)

object StatusChangeEvent {
  implicit val statusChangeEventFormat: Format[StatusChangeEvent] = new Format[StatusChangeEvent] {
    override def reads(json: JsValue): JsResult[StatusChangeEvent] = {
      val time = Instant.ofEpochMilli((json \ "time").as[Long]).atZone(ZoneOffset.UTC).toLocalDateTime
      val status = InvitationStatus((json \ "status").as[String])
      JsSuccess(StatusChangeEvent(time, status))
    }

    override def writes(o: StatusChangeEvent): JsValue =
      Json.obj(
        "time"   -> o.time.toInstant(ZoneOffset.UTC).toEpochMilli,
        "status" -> o.status.toString
      )
  }
}

case class Invitation(
  _id: ObjectId = ObjectId.get(),
  invitationId: InvitationId,
  arn: Arn,
  clientType: Option[String],
  service: Service,
  clientId: ClientId,
  suppliedClientId: ClientId,
  expiryDate: LocalDate,
  detailsForEmail: Option[DetailsForEmail],
  isRelationshipEnded: Boolean = false,
  relationshipEndedBy: Option[String] = None,
  clientActionUrl: Option[String],
  origin: Option[String] = None,
  events: List[StatusChangeEvent]
) {

  def firstEvent(): StatusChangeEvent =
    events.head

  def mostRecentEvent(): StatusChangeEvent =
    events.last

  def status: InvitationStatus = mostRecentEvent().status

  def isPendingOn(date: LocalDate): Boolean = status == Pending && date.isBefore(expiryDate)

  val altItsa: Option[Boolean] = if (service == MtdIt) Some(clientId == suppliedClientId) else None

}

object Invitation {

  def createNew(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: LocalDateTime,
    expiryDate: LocalDate,
    origin: Option[String]
  ): Invitation =
    Invitation(
      invitationId = InvitationId.create(arn.value, clientId.value, service.id)(service.invitationIdPrefix),
      arn = arn,
      clientType = clientType,
      service = service,
      clientId = clientId,
      suppliedClientId = suppliedClientId,
      expiryDate = expiryDate,
      detailsForEmail = detailsForEmail,
      clientActionUrl = None,
      origin = origin,
      events = List(StatusChangeEvent(startDate, Pending))
    )

  implicit val dateTimeFormats: Format[LocalDateTime] = MongoLocalDateTimeFormat.localDateTimeFormat
  implicit val localDateFormats: Format[LocalDate] = MongoLocalDateTimeFormat.localDateFormat
  implicit val oidFormat: Format[ObjectId] = MongoFormats.Implicits.objectIdFormat

  object external {
    implicit val writes: Writes[Invitation] = new Writes[Invitation] {
      def writes(invitation: Invitation) =
        Json.obj(
          "clientType"           -> invitation.clientType,
          "service"              -> invitation.service.id,
          "clientIdType"         -> invitation.clientId.typeId,
          "clientId"             -> invitation.clientId.value,
          "arn"                  -> invitation.arn.value,
          "suppliedClientId"     -> invitation.suppliedClientId.value,
          "suppliedClientIdType" -> invitation.suppliedClientId.typeId,
          "created"              -> invitation.firstEvent().time.format(DateTimeFormatter.ISO_DATE_TIME),
          "lastUpdated"          -> invitation.mostRecentEvent().time.format(DateTimeFormatter.ISO_DATE_TIME),
          "expiryDate"           -> invitation.expiryDate.format(DateTimeFormatter.ISO_DATE),
          "status"               -> invitation.status,
          "invitationId"         -> invitation.invitationId.value,
          "detailsForEmail"      -> invitation.detailsForEmail,
          "isRelationshipEnded"  -> invitation.isRelationshipEnded,
          "relationshipEndedBy"  -> invitation.relationshipEndedBy,
          "clientActionUrl"      -> invitation.clientActionUrl,
          "origin"               -> invitation.origin
        )
    }
  }

  def toInvitationInfo(i: Invitation): InvitationInfo =
    InvitationInfo(
      i.invitationId,
      i.expiryDate,
      i.status,
      i.arn,
      i.service,
      i.isRelationshipEnded,
      i.relationshipEndedBy,
      i.events,
      (i.service == Service.MtdIt && i.suppliedClientId == i.clientId) || i.status == PartialAuth
    )
}

/** Information provided by the agent to offer representation to HMRC */
case class AgentInvitation(service: String, clientType: Option[String], clientIdType: String, clientId: String) {

  lazy val getService: Service = Service.forId(service)
}

object AgentInvitation {
  implicit val format: OFormat[AgentInvitation] = Json.format[AgentInvitation]

  def normalizeClientId(clientId: String): String = clientId.replaceAll("\\s", "")
}

case class InvitationInfo(
  invitationId: InvitationId,
  expiryDate: LocalDate,
  status: InvitationStatus,
  arn: Arn,
  service: Service,
  isRelationshipEnded: Boolean = false,
  relationshipEndedBy: Option[String] = None,
  events: List[StatusChangeEvent],
  isAltItsa: Boolean = false
)

object InvitationInfo {
  implicit val format: OFormat[InvitationInfo] = Json.format[InvitationInfo]
}
