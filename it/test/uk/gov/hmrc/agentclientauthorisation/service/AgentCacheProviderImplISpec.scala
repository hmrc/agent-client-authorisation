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

package uk.gov.hmrc.agentclientauthorisation.service

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.{AgencyInvitationsController, AgentServicesController, BaseISpec}

class AgentCacheProviderImplISpec extends BaseISpec {

  override lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .configure(
      Map(
        "agent.cache.size"    -> 100,
        "agent.cache.expires" -> "1 hour"
      )
    )

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]
  lazy val asaController: AgentServicesController = app.injector.instanceOf[AgentServicesController]

  "AgentCacheProvider.cgtSubscriptionCache" should {
    "work as expected" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}").withHeaders(("Authorization" -> "Bearer testtoken"))

      val result1 = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result1) shouldBe 200

      val result2 = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result2) shouldBe 200

      val result3 = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result3) shouldBe 200

      // verify DES call is made only once
      verify(1, getRequestedFor(urlEqualTo(s"/subscriptions/CGT/ZCGT/${cgtRef.value}")))

    }
  }
  "AgentCacheProvider.trustCache" should {
    "work as expected" in {
      val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustName(utr.value, 200, trustNameJson)

      val request = FakeRequest("GET", s"/trusts/agent-known-fact-check/${utr.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result1 = await(controller.getTrustName(utr.value)(request))
      status(result1) shouldBe 200

      val result2 = await(controller.getTrustName(utr.value)(request))
      status(result2) shouldBe 200

      // verify DES call is made only once
      verify(1, getRequestedFor(urlEqualTo(s"/trusts/agent-known-fact-check/${utr.value}")))
    }
  }
  "AgentCacheProvider.trustNTCache" should {
    "work as expected" in {
      val trustNameJson = """{"trustDetails": {"trustName": "James Nelson Trust"}}"""

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustName(urn.value, 200, trustNameJson)

      val request = FakeRequest("GET", s"/trusts/agent-known-fact-check/${urn.value}").withHeaders(("Authorization" -> "Bearer testtoken"))

      val result1 = await(controller.getTrustName(urn.value)(request))
      status(result1) shouldBe 200

      val result2 = await(controller.getTrustName(urn.value)(request))
      status(result2) shouldBe 200

      // verify DES call is made only once
      verify(1, getRequestedFor(urlEqualTo(s"/trusts/agent-known-fact-check/${urn.value}")))
    }
  }
  "AgentCacheProvider.agencyDetailsCache" should {
    "work as expected" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))

      val request = FakeRequest("GET", s"/agent/agency-name")

      val result1 = await(asaController.getAgencyNameBy(Right(arn))(request))
      status(result1) shouldBe 200

      val result2 = await(asaController.getAgencyNameBy(Right(arn))(request))
      status(result2) shouldBe 200

      val result3 = await(asaController.getAgencyNameBy(Right(arn))(request))
      status(result3) shouldBe 200

      // verify DES call is made only once
      verify(1, getRequestedFor(urlEqualTo(s"/registration/personal-details/arn/${arn.value}")))

    }
  }
}
