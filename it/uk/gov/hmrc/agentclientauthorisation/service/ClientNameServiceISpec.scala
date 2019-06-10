package uk.gov.hmrc.agentclientauthorisation.service
import uk.gov.hmrc.agentclientauthorisation.controllers.BaseISpec
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global

class ClientNameServiceISpec extends BaseISpec {

  val service = app.injector.instanceOf[ClientNameService]

  "getClientNameByService" should {
    "get the trading name if the service is ITSA" in {
      givenTradingName(Nino("AB123456A"), "Mr Tradington")
      val result = await(service.getClientNameByService("AB123456A", Service.MtdIt))

      result shouldBe Some("Mr Tradington")
    }
    "get the citizen name if the service is AFI" in {
      givenCitizenDetailsAreKnownFor("AB123456A", "12231998")
      val result = await(service.getClientNameByService("AB123456A", Service.PersonalIncomeRecord))

      result shouldBe Some("John Smith")
    }
    "get the vat name if the service is VAT" in {
      givenClientDetails(Vrn("101747641"))
      val result = await(service.getClientNameByService("101747641", Service.Vat))

      result shouldBe Some("GDT")
    }
    "return None is the clientId type and service do not match" in {
      val result = await(service.getClientNameByService("AB123456A", Service.Vat))

      result shouldBe None
    }
  }
  "getItsaTradingName" should {
    "get the trading name if it is found" in {
      givenTradingName(Nino("AB123456A"), "Mr Tradington")

      val result = await(service.getItsaTradingName(MtdItId("LCLG57411010846")))
      result shouldBe Some("Mr Tradington")
    }
    "get the citizen name if there is no trading name" in {
      givenCitizenDetailsAreKnownFor("AB123456A", "12231998")

      val result = await(service.getItsaTradingName(MtdItId("LCLG57411010846")))
      result shouldBe Some("John Smith")
    }
  }
  "getVatName" should {
    "get the trading name if it is present" in {
      givenClientDetails(Vrn("101747641"))
      val result = await(service.getVatName(Vrn("101747641")))

      result shouldBe Some("GDT")
    }
    "get the organisation name if there is no trading name" in {
      givenClientDetailsOnlyOrganisation(Vrn("101747641"))
      val result = await(service.getVatName(Vrn("101747641")))
      result shouldBe Some("Gadgetron")
    }
    "get the individual name if there is no trading name or organisation name" in {
      givenClientDetailsOnlyPersonal(Vrn("101747641"))
      val result = await(service.getVatName(Vrn("101747641")))
      result shouldBe Some("Mr Winston H Greenburg")
    }
  }

}
