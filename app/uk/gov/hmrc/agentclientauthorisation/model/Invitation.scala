/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.domain.{SimpleObjectReads, SimpleObjectWrites}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats


case class Arn(arn: String)
case class MtdClientId(value: String)

sealed trait InvitationStatus
case object Pending extends InvitationStatus
case object Rejected extends InvitationStatus
case object Accepted extends InvitationStatus
case object Cancelled extends InvitationStatus

object InvitationStatus {
  def unapply(status: InvitationStatus) : Option[String] = status match {
    case Pending => Some("Pending")
    case Rejected => Some("Rejected")
    case Accepted => Some("Accepted")
    case Cancelled => Some("Cancelled")
    case _ => None
  }

  def parse(status: String): Either[String, InvitationStatus] = status.toLowerCase match {
    case "pending" =>   Right(Pending)
    case "rejected" =>  Right(Rejected)
    case "accepted" =>  Right(Accepted)
    case "cancelled" => Right(Cancelled)
    case _ => Left(s"status of [$status] is not a valid InvitationStatus")
  }

  implicit val invitationStatusFormat = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = parse(json.as[String]) match {
      case Right(status) => JsSuccess(status)
      case Left(error) => JsError(error)
    }

    override def writes(o: InvitationStatus): JsValue = unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }

}

case class StatusChangeEvent(time: DateTime, status: InvitationStatus)

case class Invitation(
  id: BSONObjectID,
  arn: Arn,
  regime: String,
  clientId: String,
  postcode: String,
  events: List[StatusChangeEvent]) {

  def firstEvent(): StatusChangeEvent = {
    events.head
  }

  def mostRecentEvent(): StatusChangeEvent = {
    events.last
  }

  def status = mostRecentEvent().status
}

/** Information provided by the agent to offer representation to HMRC */
case class AgentInvite(
  regime: String,
  clientId: String,
  postcode: String)

object StatusChangeEvent {
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
}

object Arn {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.arn)
}

object Invitation {
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "id" -> invitation.id.stringify,
      "regime" -> invitation.regime,
      "clientId" -> invitation.clientId,
      "postcode" -> invitation.postcode,
      "arn" -> invitation.arn.arn,
      "created" -> invitation.firstEvent().time,
      "lastUpdated" -> invitation.mostRecentEvent().time,
      "status" -> invitation.status

    )

  }
  val mongoFormats = ReactiveMongoFormats.mongoEntity(Json.format[Invitation])
}

object AgentInvite {
  implicit val format = Json.format[AgentInvite]
}
