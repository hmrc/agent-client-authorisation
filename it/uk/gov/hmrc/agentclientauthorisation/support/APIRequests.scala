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

import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import views.html.helper._


trait APIRequests {

  def sandboxMode:Boolean = false

  private def baseUrl = if(sandboxMode) "/agent-client-authorisation/sandbox" else "/agent-client-authorisation"
  private def agencyGetInvitationsUrl(arn: Arn): String = s"$baseUrl/agencies/${arn.arn}/invitations/sent"
  private def agencyGetInvitationUrl(arn: Arn, invitationId: String): String = s"$baseUrl/agencies/${arn.arn}/invitations/sent/$invitationId"

  def agencyGetSentInvitations(arn: Arn, filteredBy: Seq[(String, String)] = Nil)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    val params = withFilterParams(filteredBy)
    new Resource(agencyGetInvitationsUrl(arn) + params, port).get()(hc)
  }

  def agencyGetSentInvitation(arn: Arn, invitationId: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    new Resource(agencyGetInvitationUrl(arn, invitationId), port).get()(hc)
  }

  case class AgencyInvitationRequest(regime: Regime, clientId: MtdClientId, postcode: String) {
    val json: String = s"""{"regime": "${regime.value}", "clientId": "${clientId.value}", "postcode": "$postcode"}"""
  }

  def agencySendInvitation(arn: Arn, invitation: AgencyInvitationRequest)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    val url: String = agencyGetInvitationsUrl(arn)
    new Resource(url, port).postAsJson(invitation.json)
  }

  def agencyCancelInvitation(arn: Arn, invitationId: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    val url: String = agencyGetInvitationUrl(arn, invitationId) + "/cancel"
    new Resource(url, port).putEmpty()
  }

  /*
  CLIENT RELATED RESOURCES
  TODO:  split these into seperate traits?
   */

  private def clientReceivedInvitationsUrl(id: MtdClientId): String = s"$baseUrl/clients/${urlEncode(id.value)}/invitations/received"
  private def clientReceivedInvitationUrl(id: MtdClientId, invitationId:String): String = s"$baseUrl/clients/${urlEncode(id.value)}/invitations/received/$invitationId"

  def clientGetReceivedInvitations(clientId: MtdClientId, filteredBy: Seq[(String, String)] = Nil)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    val params = withFilterParams(filteredBy)
    new Resource(clientReceivedInvitationsUrl(clientId) + params, port).get()(hc)
  }

  def clientGetReceivedInvitation(clientId: MtdClientId, invitationId: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    new Resource(clientReceivedInvitationUrl(clientId, invitationId), port).get()(hc)
  }

  def clientAcceptInvitation(clientId: MtdClientId, invitationId:String)(implicit port: Int, hc: HeaderCarrier) = {
    updateInvitationResource(clientReceivedInvitationsUrl(clientId) + s"/$invitationId/accept")
  }

  def clientRejectInvitation(clientId: MtdClientId, invitationId:String)(implicit port: Int, hc: HeaderCarrier) =
    updateInvitationResource(clientReceivedInvitationsUrl(clientId) + s"/$invitationId/reject")

  def updateInvitationResource(link: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    println(link)
    new Resource(link, port).putEmpty()(hc)
  }

  def withFilterParams(filteredBy: Seq[(String, String)]): String = {
    filteredBy match {
      case Nil => ""
      case (k, v) :: Nil => s"?$k=$v"
      case (k, v) :: tail => s"?$k=$v" + tail.map(params => s"&${params._1}=${params._2}").mkString
    }
  }
}
