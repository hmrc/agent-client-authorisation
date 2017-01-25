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

package uk.gov.hmrc.agentclientauthorisation.support

import play.mvc.Http.HeaderNames._
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers._
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}

class AgencyApi(apiRequests: ApiRequests, val arn: Arn, implicit val port: Int) {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(arn.arn)))

  def sendInvitation(clientId: MtdClientId, regime: Regime = Regime("mtd-sa"), postcode:String = "AA1 1AA"): String = {

    val response = apiRequests.agencySendInvitation(arn, apiRequests.AgencyInvitationRequest(regime, clientId, postcode))
    require(response.status == 201, s"Creating an invitation should return 201, was [${response.status}]")
    response.header(LOCATION).get
  }

  def sentInvitations(filteredBy:Seq[(String, String)] = Nil): HalResourceHelper = {

    val response = apiRequests.agencyGetSentInvitations(arn, filteredBy)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }

  def sentInvitation(invitationId:String): HalResourceHelper = {

    val response = apiRequests.agencyGetSentInvitation(arn, invitationId)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }

  def cancelInvitation(invitation: EmbeddedInvitation): HttpResponse = {

    invitation.links.cancelLink.map { cancelLink =>
      val response: HttpResponse = new Resource(cancelLink, port).putEmpty()(hc)
      require(response.status == 204, s"response for canceling invitation should be 204, was [${response.status}]")
      response
    } .getOrElse (throw new IllegalStateException("Can't cancel this invitation the cancel link is not defined"))
  }
}
