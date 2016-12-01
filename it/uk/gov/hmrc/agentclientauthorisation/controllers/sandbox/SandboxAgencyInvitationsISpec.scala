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
import play.api.libs.json.{JsArray, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.APIRequests

class SandboxAgencyInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with APIRequests {
  private val REGIME = "mtd-sa"
  private val agentCode = AgentCode("A12345A")
  private implicit val arn = Arn("ABCDEF12345678")
  private val createInvitationUrl = s"/agent-client-authorisation/sandbox/agencies/${arn.arn}/invitations"
  private def getInvitationUrl = s"${agencyGetInvitationsUrl(arn)}/"

  override val sandboxMode = true

  "GET /sandbox" should {
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(baseUrl)
    behave like anEndpointAccessibleForMtdAgentsOnly(rootResource)
  }

  "GET /sandbox/agencies" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agenciesResource)
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agenciesUrl)
  }

  "GET /sandbox/agencies/:arn" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyResource(arn))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agencyUrl(arn))
  }

  "GET /sandbox/agencies/:arn/invitations" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agencyInvitationsUrl(arn))
  }

  "POST of /sandbox/agencies/:arn/invitations" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForCreateInvitation())

    "return a 201 response with a location header" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = responseForCreateInvitation()

      response.status shouldBe 201
      response.header("location").get should startWith(agencyGetInvitationsUrl(arn))
    }
  }

  "PUT of /sandbox/agencies/:arn/invitations/received/:invitationId/cancel" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForCancelInvitation())

    "return a 204 response code" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = responseForCancelInvitation()

      response.status shouldBe 204
    }
  }

  "GET /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn))

    "return some invitations" in {
      val testStartTime = now().getMillis
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      
      val response = agencyGetSentInvitations(arn)

      response.status shouldBe 200
      val invitations = (response.json \ "_embedded" \ "invitations").as[JsArray].value
      val invitationLinks = (response.json \ "_links" \ "invitation" \\ "href").map(_.as[String])

      invitations.size shouldBe 2
      checkInvitation(invitations.head, testStartTime)
      checkInvitation(invitations(1), testStartTime)
      invitations.map(selfLink) shouldBe invitationLinks

      selfLink(response.json) shouldBe agencyGetInvitationsUrl(arn)
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
    val selfHref = selfLink(invitation)
    selfHref should startWith(s"/agent-client-authorisation/sandbox/agencies/${arn.arn}/invitations/sent")
    (invitation \ "_links" \ "cancel" \ "href").as[String] shouldBe s"$selfHref/cancel"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn").as[String] shouldBe arn.arn
    (invitation \ "regime").as[String] shouldBe REGIME
    (invitation \ "clientId").as[String] shouldBe "clientId"
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }

  def selfLink(obj: JsValue): String = {
    (obj \ "_links" \ "self" \ "href").as[String]
  }

  def responseForGetInvitation() = {
    new Resource(getInvitationUrl + "invitationId", port).get()
  }

  def responseForCreateInvitation() = {
    new Resource(createInvitationUrl, port).postAsJson(s"""{"regime": "$REGIME", "clientId": "clientId", "postcode": "AA1 1AA"}""")
  }

  def anEndpointWithMeaningfulContentForAnAuthorisedAgent(url:String): Unit = {
    "return a meaningful response for the authenticated agent" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe url
      (response.json \ "_links" \ "sent" \ "href").as[String] shouldBe agencyGetInvitationsUrl(arn)
    }
  }

  private def responseForCancelInvitation(invitationUri: URI = new URI(getInvitationUrl + "none")) = {
    new Resource(invitationUri.toString + "/cancel", port).putEmpty()
  }
}
