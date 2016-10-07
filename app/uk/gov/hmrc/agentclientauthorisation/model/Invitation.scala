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
import uk.gov.hmrc.domain.{CtUtr, SaUtr, SimpleObjectReads, SimpleObjectWrites}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats


case class Arn(arn: String)

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

  def apply(status: String) = status.toLowerCase match {
    case "pending" =>   Pending
    case "rejected" =>  Rejected
    case "accepted" =>  Accepted
    case "cancelled" => Cancelled
    case unknown => throw new IllegalArgumentException(s"status of [$unknown] is not a valid InvitationStatus")
  }

  implicit val authorisationStatusFormat = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = JsSuccess(apply(json.as[String]))
    override def writes(o: InvitationStatus): JsValue = unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }

}

case class StatusChangeEvent(time: DateTime, status: InvitationStatus)

case class Invitation(
  id: BSONObjectID,
  arn: Arn,
  regime: String,
  customerRegimeId: String,
  postcode: String,
  events: List[StatusChangeEvent]) {
  val customerSaUtr = regime match {
    case "sa" => Some(SaUtr(customerRegimeId))
    case _ => None
  }

  val customerCtUtr = regime match {
    case "ct" => Some(CtUtr(customerRegimeId))
    case _ => None
  }
}

case class AgentClientAuthorisationHttpRequest(
  arn: Arn,
  regime: String,
  customerRegimeId: String,
  postcode: String)

object StatusChangeEvent {
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
}

object Invitation {
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.arn)
  implicit val jsonFormats = Json.format[Invitation]
  val mongoFormats = ReactiveMongoFormats.mongoEntity(jsonFormats)
}

object AgentClientAuthorisationHttpRequest {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.arn)
  implicit val format = Json.format[AgentClientAuthorisationHttpRequest]
}
