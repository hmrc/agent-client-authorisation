/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers

import org.apache.pekko.stream.Materializer
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._

class AgentGetTrustNameISpec extends BaseISpec {

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  "GET /known-facts/organisations/trust/:utr" should {

    val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

    val invalidTrustJson =
      """{"code": "INVALID_TRUST_STATE","reason": "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible."}"""

    val notFoundJson =
      """{"code": "RESOURCE_NOT_FOUND","reason": "The remote endpoint has indicated that the trust is not found."}"""

    val request = FakeRequest("GET", s"/known-facts/organisations/trust/${utr.value}").withHeaders("Authorization" -> "Bearer testtoken")

    "return success response for a given utr" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustName(utr.value, 200, trustNameJson)

      val result = controller.getTrustName(utr.value)(request)
      status(result) shouldBe 200

      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))

    }

    "return success response if trust is with in INVALID_TRUST_STATE for a given utr" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustName(utr.value, 400, invalidTrustJson)

      val result = controller.getTrustName(utr.value)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(
        Left(InvalidTrust("INVALID_TRUST_STATE", "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible."))
      )
    }

    "handles 404 response from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustName(utr.value, 404, notFoundJson)

      val result = controller.getTrustName(utr.value)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(
        Left(InvalidTrust("RESOURCE_NOT_FOUND", "The remote endpoint has indicated that the trust is not found."))
      )
    }
  }
}
