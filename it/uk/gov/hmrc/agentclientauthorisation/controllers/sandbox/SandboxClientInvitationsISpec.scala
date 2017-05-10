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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.test.UnitSpec

class SandboxClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests {

  private implicit val arn = Arn("ABCDEF12345678")
  private val nino = HardCodedSandboxIds.clientId

  override val sandboxMode: Boolean = true

  "GET /sandbox" should {
    behave like anEndpointWithClientReceivedInvitationsLink(baseUrl)
  }

  "GET /sandbox/clients" should {
    behave like anEndpointWithClientReceivedInvitationsLink(clientsUrl)
  }

  "GET /sandbox/clients/:clientId" should {
    behave like anEndpointWithClientReceivedInvitationsLink(clientUrl(nino))
  }

  "GET /sandbox/clients/:clientId/invitations" should {
    "return a meaningful response" in {
      val url = clientReceivedInvitationsUrl(nino)

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
    }
  }

  "PUT of /sandbox/clients/:clientId/invitations/received/:invitationId/accept" should {
    "return a 204 response code" in {

      val response = clientAcceptInvitation(nino, "invitationId")
      response.status shouldBe 204
    }
  }

  "PUT of /sandbox/clients/:clientId/invitations/received/:invitationId/reject" should {
    "return a 204 response code" in {
      val response = clientRejectInvitation(nino, "invitationId")
      response.status shouldBe 204
    }
  }

  "GET /clients/:clientId/invitations/received" should {
    "return some invitations" in {

      val testStartTime = now().getMillis

      val response: HalResourceHelper = HalTestHelpers(clientGetReceivedInvitations(nino).json)

      response.embedded.invitations.size shouldBe 2
      checkInvitation(nino, response.firstInvitation.underlying, testStartTime)
      checkInvitation(nino, response.secondInvitation.underlying, testStartTime)
      response.links.selfLink shouldBe s"/agent-client-authorisation/clients/${nino.value}/invitations/received"
      response.embedded.invitations.map(_.links.selfLink) shouldBe response.links.invitations
    }
  }

  "GET /clients/:clientId/invitations/received/invitationId" should {
    "return an invitation" in {
      val testStartTime = now().getMillis

      val response = clientGetReceivedInvitation(nino, "invitationId")
      response.status shouldBe 200
      checkInvitation(nino, response.json, testStartTime)
    }
  }

  def anEndpointWithClientReceivedInvitationsLink(url: String): Unit = {
    "return a HAL response including a client received invitations link" in {
      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe externalUrl(clientReceivedInvitationsUrl(nino))
    }
   }

  private def checkInvitation(clientId: Nino, invitation: JsValue, testStartTime: Long): Unit = {

    def selfLink: String = (invitation \ "_links" \ "self" \ "href").as[String]

    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    selfLink should startWith(s"/agent-client-authorisation/clients/${clientId.value}/invitations/received/")
    (invitation \ "_links" \ "accept" \ "href").as[String] shouldBe s"$selfLink/accept"
    (invitation \ "_links" \ "reject" \ "href").as[String] shouldBe s"$selfLink/reject"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn").as[String] shouldBe "agencyReference"
    (invitation \ "service").as[String] shouldBe "HMRC-MTD-IT"
//TODO    (invitation \ "clientIdType").as[String] shouldBe "ni"
    (invitation \ "clientId").as[String] shouldBe clientId.value
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }
}
