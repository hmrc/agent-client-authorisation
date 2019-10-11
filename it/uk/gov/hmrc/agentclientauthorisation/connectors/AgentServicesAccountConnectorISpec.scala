package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.agentclientauthorisation.model.{AgencyEmailNotFound, CustomerDetails, Individual}
import uk.gov.hmrc.agentclientauthorisation.support.{AgentServicesAccountStub, AppAndStubs}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgentServicesAccountConnectorISpec extends UnitSpec with AppAndStubs with AgentServicesAccountStub {

  val connector = app.injector.instanceOf[AgentServicesAccountConnector]

  val arn = Arn("TARN0000001")
  val nino = Nino("AB123456A")
  val mtdItId = MtdItId("LC762757D")
  val vrn = Vrn("101747641")

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
  "getAgencyNameViaClient" should {
    "return an agency name for a valid arn" in {
      givenGetAgencyNameViaClientStub(arn)
      val result = await(connector.getAgencyNameViaClient(arn))

      result shouldBe Some("My Agency")
    }
    "return AgencyNameNotFound exception when there is no agencyName" in {
      givenAgencyNameNotFoundClientStub(arn)

      intercept[AgencyNameNotFound] {
        await(connector.getAgencyNameViaClient(arn))
      }
    }
  }
  "getAgencyEmailBy" should {
    "return an agency email for a valid arn" in {
      givenGetAgencyEmailAgentStub(arn)

      val result = await(connector.getAgencyEmailBy(arn))

      result shouldBe "agent@email.com"
    }
    "return AgencyEmailNotFound exception when there is no agency email" in {
      givenNotFoundAgencyEmailAgentStub(arn)

      intercept[AgencyEmailNotFound] {
        await(connector.getAgencyEmailBy(arn))
      }
    }
  }
  "getTradingName" should {
    "return a trading name for a valid nino" in {
      givenTradingName(nino, "Mr Trades")
      val result = await(connector.getTradingName(nino))
      result shouldBe Some("Mr Trades")
    }
    "return None when there is no trading name" in {
      givenTradingNameMissing(nino)
      val result = await(connector.getTradingName(nino))
      result shouldBe None
    }
    "return None when there is no record" in {
      givenTradingNameNotFound(nino)
      val result = await(connector.getTradingName(nino))
      result shouldBe None
    }
  }
  "getCustomerDetails" should {
    "return a CustomerDetails object for a valid vrn" in {
      givenClientDetailsForVat(vrn)
      val result = await(connector.getCustomerDetails(vrn))
      result shouldBe CustomerDetails(
        Some("Gadgetron"),
        Some(Individual(Some("Mr"), Some("Winston"), Some("H"), Some("Greenburg"))),
        Some("GDT"))
    }
    "return an empty CustomerDetails object for not found record" in {
      givenClientDetailsNotFound(vrn)
      val result = await(connector.getCustomerDetails(vrn))
      result shouldBe CustomerDetails(None, None, None)
    }
  }
  "getNinoForMtdItId" should {
    "return the corresponding nino for an MtdItId" in {
      givenNinoForMtdItId(mtdItId, nino)
      val result = await(connector.getNinoForMtdItId(mtdItId))
      result shouldBe Some(nino)
    }
    "return None when there is no nino for a mtdItId" in {
      givenNinoNotFoundForMtdItId(mtdItId)
      val result = await(connector.getNinoForMtdItId(mtdItId))
      result shouldBe None
    }
  }
}
