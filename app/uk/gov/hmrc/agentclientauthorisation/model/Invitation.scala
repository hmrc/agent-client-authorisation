/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JodaWrites._
import play.api.libs.json.JodaReads._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.http.controllers.RestFormats

sealed trait InvitationStatus {

  def toEither: Either[String, InvitationStatus] = this match {
    case Unknown(status) => Left(status)
    case status          => Right(status)
  }

  def leftMap[X](f: String => X) =
    toEither.left.map(f)

  override def toString = InvitationStatus.unapply(this).getOrElse("Unknown")
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

  implicit val invitationStatusFormat = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = apply(json.as[String]) match {
      case Unknown(value) => JsError(s"Status of [$value] is not a valid InvitationStatus")
      case value          => JsSuccess(value)
    }

    override def writes(o: InvitationStatus): JsValue =
      unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }
}

case class StatusChangeEvent(time: DateTime, status: InvitationStatus)

object StatusChangeEvent {
  implicit val statusChangeEventFormat = new Format[StatusChangeEvent] {
    override def reads(json: JsValue): JsResult[StatusChangeEvent] = {
      val time = new DateTime((json \ "time").as[Long])
      val status = InvitationStatus((json \ "status").as[String])
      JsSuccess(StatusChangeEvent(time, status))
    }

    override def writes(o: StatusChangeEvent): JsValue =
      Json.obj(
        "time"   -> o.time.getMillis,
        "status" -> o.status.toString
      )
  }
}

case class Invitation(
  id: BSONObjectID = BSONObjectID.generate(),
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
  events: List[StatusChangeEvent]) {

  def firstEvent(): StatusChangeEvent =
    events.head

  def mostRecentEvent(): StatusChangeEvent =
    events.last

  def status = mostRecentEvent().status

  def isPendingOn(date: LocalDate): Boolean = status == Pending && date.isBefore(expiryDate)

}

object Invitation {

  def createNew(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: DateTime,
    expiryDate: LocalDate,
    origin: Option[String]): Invitation =
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

  implicit val dateWrites = RestFormats.dateTimeWrite
  implicit val dateReads = RestFormats.dateTimeRead

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
          "created"              -> invitation.firstEvent().time,
          "lastUpdated"          -> invitation.mostRecentEvent().time,
          "expiryDate"           -> invitation.expiryDate,
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
}

/** Information provided by the agent to offer representation to HMRC */
case class AgentInvitation(service: String, clientType: Option[String], clientIdType: String, clientId: String) {

  lazy val getService = Service.forId(service)
}

object AgentInvitation {
  implicit val format = Json.format[AgentInvitation]

  def normalizeClientId(clientId: String) = clientId.replaceAll("\\s", "")
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
  isAltItsa: Boolean = false)

object InvitationInfo {
  implicit val format = Json.format[InvitationInfo]
}
