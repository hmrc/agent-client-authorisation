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

import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.logging.SessionId
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class ClientApi(val apiRequests: ApiRequests, val suppliedClientId: Nino, val clientId: ClientId = MtdItId("mtdItId"), implicit val port: Int) {

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(suppliedClientId.value)))

  def acceptInvitation(invitation: EmbeddedInvitation): HttpResponse = {
    invitation.links.acceptLink.map (apiRequests.updateInvitationResource)
      .getOrElse (throw new IllegalStateException("Can't accept this invitation the accept link is not defined"))
  }

  def rejectInvitation(invitation: EmbeddedInvitation): HttpResponse = {
    invitation.links.rejectLink.map(apiRequests.updateInvitationResource)
      .getOrElse (throw new IllegalStateException("Can't reject this invitation the reject link is not defined"))
  }

  def getInvitations(filteredBy: Seq[(String, String)] = Nil): HalResourceHelper = {
    val response = apiRequests.clientGetReceivedInvitations(clientId, filteredBy)(port, hc)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }
}
