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
import play.api.libs.json.{JsArray, JsValue}
import uk.gov.hmrc.agentclientauthorisation.support.{ApiRequests, MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.test.UnitSpec

class SandboxAgencyInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests {
  private implicit val arn = HardCodedSandboxIds.arn
  private val MtdRegime: Regime = Regime("mtd-sa")
  private val validInvitation: AgencyInvitationRequest = AgencyInvitationRequest(MtdRegime, "AA123456A", "AA1 1AA")

  override val sandboxMode = true

  "GET /sandbox" should {
    behave like anEndpointWithAgencySentInvitationsLink(baseUrl)
  }

  "GET /sandbox/agencies" should {
    behave like anEndpointWithAgencySentInvitationsLink(agenciesUrl)
  }

  "GET /sandbox/agencies/:arn" should {
    behave like anEndpointWithAgencySentInvitationsLink(agencyUrl(arn))
  }

  "GET /sandbox/agencies/:arn/invitations" should {
    behave like anEndpointWithAgencySentInvitationsLink(agencyInvitationsUrl(arn))
  }

  "POST of /sandbox/agencies/:arn/invitations/sent" should {
    "return a 201 response with a location header" in {
      val response = agencySendInvitation(arn, validInvitation)

      response.status shouldBe 201
      response.header("location").get should startWith(externalUrl(agencyGetInvitationsUrl(arn)))
    }
  }

  "PUT of /sandbox/agencies/:arn/invitations/received/:invitationId/cancel" should {
    "return a 204 response code" in {
      val response = agencyCancelInvitation(arn, "some")

      response.status shouldBe 204
    }
  }

  "GET /agencies/:arn/invitations/sent" should {
    "return some invitations" in {
      val testStartTime = now().getMillis

      val response = agencyGetSentInvitations(arn)

      response.status shouldBe 200
      val invitations = (response.json \ "_embedded" \ "invitations").as[JsArray].value
      val invitationLinks = (response.json \ "_links" \ "invitations" \\ "href").map(_.as[String])

      invitations.size shouldBe 2
      checkInvitation(invitations.head, testStartTime)
      checkInvitation(invitations(1), testStartTime)
      invitations.map(selfLink) shouldBe invitationLinks

      selfLink(response.json) shouldBe externalUrl(agencyGetInvitationsUrl(arn))
    }
  }

  "GET /agencies/:arn/invitations/sent/invitationId" should {
    "return an invitation" in {
      val testStartTime = now().getMillis

      val response = agencyGetSentInvitation(arn, "some")

      response.status shouldBe 200
      checkInvitation(response.json, testStartTime)
    }
  }

  private def checkInvitation(invitation: JsValue, testStartTime: Long): Unit = {
    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val selfHref = selfLink(invitation)
    selfHref should startWith(s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent")
    (invitation \ "_links" \ "cancel" \ "href").as[String] shouldBe s"$selfHref/cancel"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn").as[String] shouldBe arn.arn
    (invitation \ "regime").as[String] shouldBe MtdRegime.value
    (invitation \ "clientId").as[String] shouldBe "clientId"
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }

  def selfLink(obj: JsValue): String = {
    (obj \ "_links" \ "self" \ "href").as[String]
  }

  def anEndpointWithAgencySentInvitationsLink(url:String): Unit = {
    "return a HAL response including an agency sent invitations link" in {
      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "sent" \ "href").as[String] shouldBe externalUrl(agencyGetInvitationsUrl(arn))
    }
  }
}
