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

import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.test.UnitSpec

class SandboxClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with APIRequests {

  private val MtdRegime = Regime("mtd-sa")
  private implicit val arn = Arn("ABCDEF12345678")
  private val mtdClientId = FakeMtdClientId.random()

  override def sandboxMode: Boolean = true
  private val rootUrl = s"/agent-client-authorisation/sandbox"
  private val clientsUrl = s"${rootUrl}/clients"
  private val clientUrl = s"${clientsUrl}/${mtdClientId.value}"
  private val invitationsUrl = s"${clientUrl}/invitations"
  private val invitationsReceivedUrl = s"${invitationsUrl}/received"

  "GET /sandbox" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(new Resource(rootUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(rootUrl)
  }

  "GET /sandbox/clients" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(new Resource(clientsUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientsUrl)
  }

  "GET /sandbox/clients/:clientId" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(new Resource(clientUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientUrl)
  }

  "GET /sandbox/clients/:clientId/invitations" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(new Resource(invitationsUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(invitationsUrl)
  }

  "PUT of /sandbox/clients/:clientId/invitations/received/:invitationId/accept" should {

    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientAcceptInvitation(mtdClientId, "none"))

    "return a 204 response code" in {

      given().client(clientId = mtdClientId).isLoggedIn()
      val response = clientAcceptInvitation(mtdClientId, "invitationId")
      response.status shouldBe 204
    }
  }

  "PUT of /sandbox/clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientRejectInvitation(mtdClientId, "invitationId"))

    "return a 204 response code" in {
      given().client(clientId = mtdClientId).isLoggedIn()
      val response = clientAcceptInvitation(mtdClientId, "invitationId")
      response.status shouldBe 204
    }
  }

  "GET /clients/:clientId/invitations/received" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientGetReceivedInvitations(mtdClientId))

    "return some invitations" in {

      val testStartTime = now().getMillis
      given().client(clientId = mtdClientId).isLoggedIn()

      val response: HalResourceHelper = HalTestHelpers(clientGetReceivedInvitations(mtdClientId).json)

      response.embedded.invitations.size shouldBe 2
      checkInvitation(mtdClientId, response.firstInvitation.underlying, testStartTime)
      checkInvitation(mtdClientId, response.secondInvitation.underlying, testStartTime)
      response.links.selfLink shouldBe s"/agent-client-authorisation/sandbox/clients/${mtdClientId.value}/invitations/received"
      response.embedded.invitations.map(_.links.selfLink) shouldBe response.links.invitations
    }
  }

  "GET /clients/:clientId/invitations/received/invitationId" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientGetReceivedInvitation(mtdClientId, "invitation-id-not-used"))

    "return an invitation" in {
      val testStartTime = now().getMillis
      given().client(clientId = mtdClientId).isLoggedIn()

      val response = clientGetReceivedInvitation(mtdClientId, "invitationId")
      response.status shouldBe 200
      checkInvitation(mtdClientId, response.json, testStartTime)
    }
  }

  def anEndpointWithMeaningfulContentForAnAuthorisedClient(url:String): Unit = {
    "return a meaningful response for the authenticated agent" in {
      given().client(clientId = mtdClientId).isLoggedIn().aRelationshipIsCreatedWith(arn)

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe url
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe invitationsReceivedUrl
    }
   }

  private def checkInvitation(clientId: MtdClientId, invitation: JsValue, testStartTime: Long): Unit = {

    def selfLink: String = (invitation \ "_links" \ "self" \ "href").as[String]

    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    selfLink should startWith(s"/agent-client-authorisation/sandbox/clients/${clientId.value}/invitations/received/")
    (invitation \ "_links" \ "accept" \ "href").as[String] shouldBe s"$selfLink/accept"
    (invitation \ "_links" \ "reject" \ "href").as[String] shouldBe s"$selfLink/reject"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn").as[String] shouldBe "agencyReference"
    (invitation \ "regime").as[String] shouldBe MtdRegime.value
    (invitation \ "clientId").as[String] shouldBe clientId.value
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }
}
