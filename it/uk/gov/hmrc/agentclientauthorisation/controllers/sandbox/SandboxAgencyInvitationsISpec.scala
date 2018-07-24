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
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsValue}
import uk.gov.hmrc.agentclientauthorisation.support.{ApiRequests, MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

class SandboxAgencyInvitationsISpec
    extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests {
  private implicit val arn = HardCodedSandboxIds.arn

  private val validInvitation: AgencyInvitationRequest =
    AgencyInvitationRequest(MtdItService, "ni", nino1.value, Some("AA1 1AA"))

  override val sandboxMode = true

  //  "GET /sandbox" should {
  //    behave like anEndpointWithAgencySentInvitationsLink(baseUrl)
  //  }

  "POST of /sandbox/agencies/:arn/invitations/sent" should {
    "return a 201 response with a location header" in {
      val response = agencySendInvitation(arn, validInvitation)

      response.status shouldBe 201
      response.header("location").get should startWith(externalUrl(agencyGetInvitationsUrl(arn)))
    }
  }

  "PUT of /sandbox/agencies/:arn/invitations/received/:invitationId/cancel" should {
    "return a 204 response code" in {
      val response = agencyCancelInvitation(arn, "ABBBBBBBBBBCC")

      response.status shouldBe 204
    }
  }

  private def checkInvitation(invitation: JsValue, testStartTime: Long): Unit = {
    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val selfHref = selfLink(invitation)
    selfHref should startWith(s"/agent-client-authorisation/agencies/${arn.value}/invitations/sent")
    (invitation \ "_links" \ "cancel" \ "href").as[String] shouldBe s"$selfHref/cancel"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn").as[String] shouldBe arn.value
    (invitation \ "service").as[String] shouldBe MtdItService
    (invitation \ "clientIdType").as[String] shouldBe "ni"
    (invitation \ "clientId").as[String] shouldBe "AA123456A"
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }

  def selfLink(obj: JsValue): String =
    (obj \ "_links" \ "self" \ "href").as[String]

}
