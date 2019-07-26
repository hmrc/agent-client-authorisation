/*
 * Copyright 2019 HM Revenue & Customs
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
import org.scalamock.scalatest.MockFactory
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, Citizen, CitizenDetailsConnector, DesConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class ClientNameServiceSpec extends UnitSpec with MockFactory {

  val mockAgentServicesAccountConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]
  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  val mockDesConnector: DesConnector = mock[DesConnector]

  val clientNameService =
    new ClientNameService(mockAgentServicesAccountConnector, mockCitizenDetailsConnector, mockDesConnector)

  val nino: Nino = Nino("AB123456A")
  val mtdItId: MtdItId = MtdItId("LCLG57411010846")
  val vrn = Vrn("555219930")
  val utr = Utr("2134514321")
  implicit val hc = HeaderCarrier()

  "getClientNameByService" should {
    "get the trading name if the service is ITSA" in {
      (mockAgentServicesAccountConnector
        .getNinoForMtdItId(_: MtdItId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(mtdItId, *, *)
        .returning(Future(Some(nino)))
      (mockAgentServicesAccountConnector
        .getTradingName(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(Some("Mr Tradington"))
      val result = await(clientNameService.getClientNameByService(mtdItId.value, Service.MtdIt))

      result shouldBe Some("Mr Tradington")
    }
    "get the citizen name if the service is AFI" in {
      (mockCitizenDetailsConnector
        .getCitizenDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(Future(Citizen(Some("Henry"), Some("Hoover"))))
      val result = await(clientNameService.getClientNameByService(nino.value, Service.PersonalIncomeRecord))

      result shouldBe Some("Henry Hoover")
    }
    "get the vat name if the service is VAT" in {
      (mockAgentServicesAccountConnector
        .getCustomerDetails(_: Vrn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(vrn, *, *)
        .returns(Future(CustomerDetails(None, None, Some("Trady name"))))
      val result = await(clientNameService.getClientNameByService(vrn.value, Service.Vat))

      result shouldBe Some("Trady name")
    }
  }
  "getItsaTradingName" should {
    "get the citizen name if there is no trading name" in {
      (mockAgentServicesAccountConnector
        .getNinoForMtdItId(_: MtdItId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(mtdItId, *, *)
        .returning(Future(Some(nino)))
      (mockAgentServicesAccountConnector
        .getTradingName(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(None)
      (mockCitizenDetailsConnector
        .getCitizenDetails(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(Future(Citizen(Some("Henry"), Some("Hoover"))))

      val result = await(clientNameService.getItsaTradingName(MtdItId("LCLG57411010846")))
      result shouldBe Some("Henry Hoover")
    }
  }
  "getVatName" should {
    "get the organisation name if there is no trading name" in {
      (mockAgentServicesAccountConnector
        .getCustomerDetails(_: Vrn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(vrn, *, *)
        .returns(
          Future(
            CustomerDetails(
              Some("Organisy name"),
              Some(Individual(Some("Miss"), Some("Marilyn"), Some("M"), Some("Monroe"))),
              None)))
      val result = await(clientNameService.getClientNameByService(vrn.value, Service.Vat))
      result shouldBe Some("Organisy name")
    }
    "get the individual name if there is no trading name or organisation name" in {
      (mockAgentServicesAccountConnector
        .getCustomerDetails(_: Vrn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(vrn, *, *)
        .returns(Future(
          CustomerDetails(None, Some(Individual(Some("Miss"), Some("Marilyn"), Some("M"), Some("Monroe"))), None)))
      val result = await(clientNameService.getClientNameByService(vrn.value, Service.Vat))
      result shouldBe Some("Miss Marilyn M Monroe")
    }
  }

  "getTrustName" should {
    "get trust name from trust details" in {
      val trustDetailsResponse = TrustResponse(Right(TrustName("Trusted")))
      (mockDesConnector
        .getTrustName(_: Utr)(_: HeaderCarrier, _: ExecutionContext))
        .expects(utr, *, *)
        .returns(Future(trustDetailsResponse))

      val result = await(clientNameService.getClientNameByService(utr.value, Service.Trust))
      result shouldBe Some("Trusted")
    }
  }

}
