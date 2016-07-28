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
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

sealed trait AuthorisationStatus
case object Pending extends AuthorisationStatus
case object Rejected extends AuthorisationStatus
case object Accepted extends AuthorisationStatus
case object Cancelled extends AuthorisationStatus
case object Expired extends AuthorisationStatus

object AuthorisationStatus {
  def unapply(status: AuthorisationStatus) : Option[String] = status match {
    case Pending => Some("Pending")
    case Rejected => Some("Rejected")
    case Accepted => Some("Accepted")
    case Cancelled => Some("Cancelled")
    case Expired => Some("Expired")
    case _ => None
  }

  def apply(status: String) = status match {
    case "Pending" =>   Pending
    case "Rejected" =>  Rejected
    case "Accepted" =>  Accepted
    case "Cancelled" => Cancelled
    case "Expired" =>   Expired
    case unknown => throw new IllegalArgumentException(s"status of [$unknown] is not a valid AuthorisationStatus")
  }

  implicit val authorisationStatusFormat = new Format[AuthorisationStatus] {
    override def reads(json: JsValue): JsResult[AuthorisationStatus] = JsSuccess(apply(json.as[String]))
    override def writes(o: AuthorisationStatus): JsValue = unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }

}

case class StatusChangeEvent(time: DateTime, status: AuthorisationStatus)

case class AgentClientAuthorisationRequest(
  id: BSONObjectID,
  agentCode: AgentCode,
  clientSaUtr: SaUtr,
  regime: String = "sa",
  events: List[StatusChangeEvent])

case class EnrichedAgentClientAuthorisationRequest(
  id: String,
  agentCode: AgentCode,
  clientSaUtr: SaUtr,
  clientFullName: Option[String],
  regime: String = "sa",
  events: List[StatusChangeEvent])

case class AgentClientAuthorisationHttpRequest(agentCode: AgentCode, clientSaUtr: SaUtr, clientPostcode: String)

object StatusChangeEvent {
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
}

object AgentClientAuthorisationRequest {
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonFormats = Json.format[AgentClientAuthorisationRequest]
  val mongoFormats = ReactiveMongoFormats.mongoEntity(jsonFormats)
}

object EnrichedAgentClientAuthorisationRequest {
  implicit val format = Json.format[EnrichedAgentClientAuthorisationRequest]
}

object AgentClientAuthorisationHttpRequest {
  implicit val format = Json.format[AgentClientAuthorisationHttpRequest]
}
