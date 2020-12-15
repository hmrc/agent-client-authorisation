/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.agentclientauthorisation.connectors.Citizen
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.MocksWithCache
import uk.gov.hmrc.agentmtdidentifiers.model.{CgtRef, MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

//TODO Convert to ISpec Maybe
class ClientNameServiceSpec extends UnitSpec with MocksWithCache {

  val clientNameService =
    new ClientNameService(mockCitizenDetailsConnector, mockDesConnector, agentCacheProvider)

  val nino: Nino = Nino("AB123456A")
  val mtdItId: MtdItId = MtdItId("LCLG57411010846")
  val vrn = Vrn("555219930")
  val utr = Utr("2134514321")
  val cgtRef = CgtRef("XMCGTP123456789")
  implicit val hc = HeaderCarrier()

  val tpd = TypeOfPersonDetails("Individual", Left(IndividualName("firstName", "lastName")))
  val tpdBus = TypeOfPersonDetails("Organisation", Right(OrganisationName("Trustee")))

  val cgtAddressDetails =
    CgtAddressDetails("line1", Some("line2"), Some("line2"), Some("line2"), "GB", Some("postcode"))

  val cgtSubscription = CgtSubscription("CGT", SubscriptionDetails(tpd, cgtAddressDetails))
  val cgtSubscriptionBus = CgtSubscription("CGT", SubscriptionDetails(tpdBus, cgtAddressDetails))

  "getClientNameByService" should {
    "get the trading name if the service is ITSA" in {
      (mockDesConnector
        .getNinoFor(_: MtdItId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(mtdItId, *, *)
        .returning(Future(Some(nino)))
      (mockDesConnector
        .getTradingNameForNino(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(Future(Some("Mr Tradington")))
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
      (mockDesConnector
        .getVatCustomerDetails(_: Vrn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(vrn, *, *)
        .returns(Future(Some(VatCustomerDetails(None, None, Some("Trady name")))))
      val result = await(clientNameService.getClientNameByService(vrn.value, Service.Vat))

      result shouldBe Some("Trady name")
    }
  }
  "getItsaTradingName" should {
    "get the citizen name if there is no trading name" in {
      (mockDesConnector
        .getNinoFor(_: MtdItId)(_: HeaderCarrier, _: ExecutionContext))
        .expects(mtdItId, *, *)
        .returning(Future(Some(nino)))
      (mockDesConnector
        .getTradingNameForNino(_: Nino)(_: HeaderCarrier, _: ExecutionContext))
        .expects(nino, *, *)
        .returns(Future(Some("")))
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
      (mockDesConnector
        .getVatCustomerDetails(_: Vrn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(vrn, *, *)
        .returns(Future(
          Some(VatCustomerDetails(Some("Organisy name"), Some(VatIndividual(Some("Miss"), Some("Marilyn"), Some("M"), Some("Monroe"))), None))))
      val result = await(clientNameService.getClientNameByService(vrn.value, Service.Vat))
      result shouldBe Some("Organisy name")
    }
    "get the individual name if there is no trading name or organisation name" in {
      (mockDesConnector
        .getVatCustomerDetails(_: Vrn)(_: HeaderCarrier, _: ExecutionContext))
        .expects(vrn, *, *)
        .returns(Future(Some(VatCustomerDetails(None, Some(VatIndividual(Some("Miss"), Some("Marilyn"), Some("M"), Some("Monroe"))), None))))
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

  "getCgtName" should {
    "get cgt name from cgt details" in {
      val cgtDetailsResponse = CgtSubscriptionResponse(Right(cgtSubscription))
      (mockDesConnector
        .getCgtSubscription(_: CgtRef)(_: HeaderCarrier, _: ExecutionContext))
        .expects(cgtRef, *, *)
        .returns(Future(cgtDetailsResponse))

      val result = await(clientNameService.getClientNameByService(cgtRef.value, Service.CapitalGains))
      result shouldBe Some("firstName lastName")
    }
  }

}
