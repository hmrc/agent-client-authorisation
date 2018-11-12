package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.agentclientauthorisation.support.{AgentServicesAccountStub, AppAndStubs}
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

class AgentServicesAccountConnectorISpec extends UnitSpec with AppAndStubs with AgentServicesAccountStub {

  val connector = app.injector.instanceOf[AgentServicesAccountConnector]

  "getAgencyNameAgent" should {
    "return an agency name for a valid arn for an agent" in {
      givenGetAgencyNameAgentStub

      val result = await(connector.getAgencyNameAgent)

      result shouldBe Some("My Agency")
    }

    "return AgencyNameNotFound exception when there is no agencyName" in {
      givenAgencyNameNotFoundAgentStub

      intercept[AgencyNameNotFound] {
        await(connector.getAgencyNameAgent)
      }

    }
  }

}
