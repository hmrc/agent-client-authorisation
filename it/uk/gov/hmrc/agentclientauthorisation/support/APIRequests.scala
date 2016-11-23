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

package uk.gov.hmrc.agentclientauthorisation.support

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



import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import views.html.helper._

trait APIRequests {

  private def agencyGetInvitationsUrl(arn:Arn): String = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
  private def agencyGetInvitationUrl(arn:Arn, invitationId:String): String = s"/agent-client-authorisation/agencies/${arn.arn}/$invitationId"
  private def clientGetReceivedInvitationsUrl(id:MtdClientId): String = s"/agent-client-authorisation/clients/${urlEncode(id.value)}/invitations/received"

  def agencyGetSentInvitationsRequest(arn:Arn, filteredBy:Seq[(String, String)] = Nil)(implicit port:Int, hc:HeaderCarrier): HttpResponse = {
    val params = withFilterParams(filteredBy)
    new Resource(agencyGetInvitationsUrl(arn)+params, port).get()(hc)
  }

  def agencyGetSentInvitationRequest(arn:Arn, invitationId:String)(implicit port:Int, hc:HeaderCarrier): HttpResponse = {
    new Resource(agencyGetInvitationUrl(arn, invitationId), port).get()(hc)
  }

  case class AgencyInvitationRequest(regime:Regime, clientId:MtdClientId, postcode:String) {
    val json: String = s"""{"regime": "${regime.value}", "clientId": "${clientId.value}", "postcode": "$postcode"}"""
  }

  def agencyPostInvitationRequest(arn:Arn, invitation:AgencyInvitationRequest)(implicit port:Int, hc:HeaderCarrier): HttpResponse = {
    val url: String = agencyGetInvitationsUrl(arn)
    new Resource(url, port).postAsJson(invitation.json)
  }

  def clientGetReceivedInvitations(filteredBy: Seq[(String, String)], clientId: MtdClientId)(implicit port:Int, hc:HeaderCarrier): HttpResponse = {
    val params = withFilterParams(filteredBy)
    new Resource(clientGetReceivedInvitationsUrl(clientId) + params, port).get()(hc)
  }

  def updateInvitationStatus(link: String)(implicit port:Int, hc:HeaderCarrier): HttpResponse = {
    val response: HttpResponse = new Resource(link, port).putEmpty()(hc)
    require(response.status == 204, s"response for PUT on invitation should be 204, was [${response.status}]")
    response
  }

  def withFilterParams(filteredBy: Seq[(String, String)]): String = {
    filteredBy match {
      case Nil => ""
      case (k, v) :: Nil => s"?$k=$v"
      case (k, v) :: tail => s"?$k=$v" + tail.map(params => s"&${params._1}=${params._2}").mkString
    }
  }
}
