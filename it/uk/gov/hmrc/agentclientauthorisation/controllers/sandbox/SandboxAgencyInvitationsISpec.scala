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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import java.net.URI
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsString, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.{FakeMtdClientId, MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class SandboxAgencyInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside {
  private val REGIME = "mtd-sa"
  private val agentCode = AgentCode("A12345A")
  private implicit val arn = Arn("ABCDEF12345678")
  private val getInvitationUrl = s"/agent-client-authorisation/sandbox/agencies/${arn.arn}/invitations/sent/"
  private val getInvitationsUrl = s"/agent-client-authorisation/sandbox/agencies/${arn.arn}/invitations/sent"

  "PUT of /sandbox/agencies/:arn/invitations/received/:invitationId/cancel" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForCancelInvitation())

    "return a 204 response code" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = responseForCancelInvitation()

      response.status shouldBe 204
    }
  }

  "GET /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForGetInvitations())

    "return some invitations" in {
      val testStartTime = now().getMillis
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      
      val response = responseForGetInvitations()

      response.status shouldBe 200
      val invitations = (response.json \ "_embedded" \ "invitations").as[JsArray].value
      val selfLinkHref = (response.json \ "_links" \ "self" \ "href").as[String]

      invitations.size shouldBe 2
      checkInvitation(invitations.head, testStartTime) 
      checkInvitation(invitations(1), testStartTime) 
      selfLinkHref shouldBe getInvitationsUrl
    }
  }

  "GET /agencies/:arn/invitations/sent/invitationId" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForGetInvitation())

    "return an invitation" in {
      val testStartTime = now().getMillis
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      
      val response = responseForGetInvitation()

      response.status shouldBe 200
      checkInvitation(response.json, testStartTime) 
    }
  }
  
  private def checkInvitation(invitation: JsValue, testStartTime: Long): Unit = {
    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val selfHref = (invitation \ "_links" \ "self" \ "href").as[String]
    selfHref should startWith(s"/agent-client-authorisation/sandbox/agencies/${arn.arn}/invitations/sent")
    (invitation \ "_links" \ "cancel" \ "href").as[String] shouldBe s"$selfHref/cancel"
    (invitation \ "arn") shouldBe JsString(arn.arn)
    (invitation \ "regime") shouldBe JsString(REGIME)
    (invitation \ "clientId") shouldBe JsString("clientId")
    (invitation \ "status") shouldBe JsString("Pending")
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }

  def responseForGetInvitation(): HttpResponse = {
    new Resource(getInvitationUrl + "invitationId", port).get()
  }

  def responseForGetInvitations(): HttpResponse = {
    new Resource(getInvitationsUrl, port).get()
  }

  private def responseForCancelInvitation(invitationUri: URI = new URI(getInvitationUrl + "none")): HttpResponse = {
    new Resource(invitationUri.toString + "/cancel", port).putEmpty()
  }
}
