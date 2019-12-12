package uk.gov.hmrc.agentclientauthorisation.service

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.{AgencyInvitationsController, AgentServicesController, BaseISpec}

class AgentCacheProviderImplISpec extends BaseISpec {

  override lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .configure(
      Map(
        "agent.cache.size"    -> 100,
        "agent.cache.expires" -> "1 hour"
      ))

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]
  lazy val asaController: AgentServicesController = app.injector.instanceOf[AgentServicesController]

  "AgentCacheProvider.cgtSubscriptionCache" should {
    "work as expected" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}")

      val result1 = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result1) shouldBe 200

      val result2 = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result2) shouldBe 200

      val result3 = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result3) shouldBe 200

      //verify DES call is made only once
      verify(1, getRequestedFor(urlEqualTo(s"/subscriptions/CGT/ZCGT/${cgtRef.value}")))

    }
  }

  "AgentCacheProvider.agencyNameCache" should {
    "work as expected" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))

      val request = FakeRequest("GET", s"/agent/agency-name")

      val result1 = await(asaController.getAgencyNameBy(arn)(request))
      status(result1) shouldBe 200

      val result2 = await(asaController.getAgencyNameBy(arn)(request))
      status(result2) shouldBe 200

      val result3 = await(asaController.getAgencyNameBy(arn)(request))
      status(result3) shouldBe 200

      //verify DES call is made only once
      verify(1, getRequestedFor(urlEqualTo(s"/registration/personal-details/arn/${arn.value}")))

    }
  }
}
