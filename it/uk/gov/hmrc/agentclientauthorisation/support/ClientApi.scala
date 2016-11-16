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

import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.MtdClientId
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.logging.SessionId
import views.html.helper._

class ClientApi(val clientId: MtdClientId, port: Int) extends Eventually {

  private val getClientInvitationUrl = s"/agent-client-authorisation/clients/${urlEncode(clientId.value)}/invitations/received"
  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(clientId.value)))


  def acceptInvitation(invitation: EmbeddedInvitation): HttpResponse = {
    invitation.links.acceptLink.map { acceptLink =>
      val response: HttpResponse = new Resource(acceptLink, port).putEmpty()(hc)
      require(response.status == 204, s"response for accepting invitation should be 204, was [${response.status}]")
      response
    } .getOrElse (throw new IllegalStateException("Can't accept this invitation the accept link is not defined"))
  }

  def rejectInvitation(invitation: EmbeddedInvitation): HttpResponse = {
    invitation.links.rejectLink.map { rejectLink =>
      val response: HttpResponse = new Resource(rejectLink, port).putEmpty()(hc)
      require(response.status == 204, s"response for rejecting invitation should be 204, was [${response.status}]")
      response
    } .getOrElse (throw new IllegalStateException("Can't reject this invitation the reject link is not defined"))
  }

  def getInvitations(filteredBy: Seq[(String, String)] = Nil): HalResourceHelper = {
    val params = withFilterParams(filteredBy)
    val response: HttpResponse = new Resource(getClientInvitationUrl+params, port).get()(hc)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }

  def withFilterParams(filteredBy: Seq[(String, String)]): String = {
    filteredBy match {
      case Nil => ""
      case (k, v) :: Nil => s"?$k=$v"
      case (k, v) :: tail => s"?$k=$v" + tail.map(params => s"&${params._1}=${params._2}").mkString
    }
  }
}
