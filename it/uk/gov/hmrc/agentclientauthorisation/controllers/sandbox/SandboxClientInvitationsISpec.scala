/*
 * Copyright 2018 HM Revenue & Customs
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
import org.scalatest.concurrent.Eventually
import org.scalatest.{ Ignore, Inside }
import play.api.libs.json.JsValue
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.mtdItId1
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.play.test.UnitSpec

@Ignore
class SandboxClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests {

  private implicit val arn = HardCodedSandboxIds.arn

  override val sandboxMode: Boolean = true

  "GET /sandbox" should {
    behave like anEndpointWithClientReceivedInvitationsLink(baseUrl)
  }

  "GET /sandbox/clients" should {
    behave like anEndpointWithClientReceivedInvitationsLink(clientsUrl)
  }

  "GET /sandbox/clients/MTDITID/:mtdItId" should {
    behave like anEndpointWithClientReceivedInvitationsLink(clientUrl(mtdItId1))
  }

  "GET /sandbox/clients/MTDITID/:mtdItId/invitations" should {
    "return a meaningful response" in {
      val url = clientReceivedInvitationsUrl(mtdItId1)

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
    }
  }

  "PUT of /sandbox/clients/MTDITID/:mtdItId/invitations/received/:invitationId/accept" should {
    "return a 204 response code" in {

      val response = clientAcceptInvitation(mtdItId1, "invitationId")
      response.status shouldBe 204
    }
  }

  "PUT of /sandbox/clients/MTDITID/:mtdItId/invitations/received/:invitationId/reject" should {
    "return a 204 response code" in {
      val response = clientRejectInvitation(mtdItId1, "invitationId")
      response.status shouldBe 204
    }
  }

  "GET /clients/MTDITID/:mtdItId/invitations/received" should {
    "return some invitations" in {

      val testStartTime = now().getMillis

      val response: HalResourceHelper = HalTestHelpers(clientGetReceivedInvitations(mtdItId1).json)

      response.embedded.invitations.size shouldBe 2
      checkInvitation(mtdItId1, response.firstInvitation.underlying, testStartTime)
      checkInvitation(mtdItId1, response.secondInvitation.underlying, testStartTime)
      response.links.selfLink shouldBe s"/agent-client-authorisation/clients/MTDITID/${mtdItId1.value}/invitations/received"
      response.embedded.invitations.map(_.links.selfLink) shouldBe response.links.invitations
    }
  }

  "GET /clients/MTDITID/:mtdItId/invitations/received/invitationId" should {
    "return an invitation" in {
      val testStartTime = now().getMillis

      val response = clientGetReceivedInvitation(mtdItId1, "invitationId")
      response.status shouldBe 200
      checkInvitation(mtdItId1, response.json, testStartTime)
    }
  }

  def anEndpointWithClientReceivedInvitationsLink(url: String): Unit = {
    "return a HAL response including a client received invitations link" in {
      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe externalUrl(clientReceivedInvitationsUrl(mtdItId1))
    }
  }

  private def checkInvitation(clientId: MtdItId, invitation: JsValue, testStartTime: Long): Unit = {

    def selfLink: String = (invitation \ "_links" \ "self" \ "href").as[String]

    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    selfLink should startWith(s"/agent-client-authorisation/clients/MTDITID/${clientId.value}/invitations/received/")
    (invitation \ "_links" \ "accept" \ "href").as[String] shouldBe s"$selfLink/accept"
    (invitation \ "_links" \ "reject" \ "href").as[String] shouldBe s"$selfLink/reject"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn").as[String] shouldBe "agencyReference"
    (invitation \ "service").as[String] shouldBe "HMRC-MTD-IT"
    (invitation \ "clientIdType").as[String] shouldBe "ni"
    (invitation \ "clientId").as[String] shouldBe clientId.value
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }
}
