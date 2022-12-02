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

package uk.gov.hmrc.agentclientauthorisation.connectors

import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, DesStubs, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PptRef, SuspensionDetails, Vrn}
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with AppAndStubs with DesStubs {

  "getBusinessDetails" should {
    "return postcode and country code for a registered client" in {
      hasABusinessPartnerRecord(nino)

      val response = await(connector.getBusinessDetails(nino)).get

      response.businessData.head.businessAddressDetails.map(_.countryCode) shouldBe Some("GB")
      response.businessData.head.businessAddressDetails.flatMap(_.postalCode) shouldBe Some("AA11AA")
      response.mtdbsa shouldBe None
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getRegistrationBusinessDetailsByNino-GET")
    }

    "return None if the client is not registered" in {
      hasNoBusinessPartnerRecord(nino)

      val response = await(connector.getBusinessDetails(nino))

      response shouldBe None
    }
  }

  "getMtdItId" should {
    "return mtdItId for a registered client" in {
      hasABusinessPartnerRecordWithMtdItId(nino, mtdItId1)

      val response = await(connector.getBusinessDetails(nino)).get

      response.businessData.head.businessAddressDetails.map(_.countryCode) shouldBe Some("GB")
      response.businessData.head.businessAddressDetails.flatMap(_.postalCode) shouldBe Some("AA11AA")
      response.mtdbsa shouldBe Some(mtdItId1)
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getRegistrationBusinessDetailsByNino-GET")
    }

    "return mtdItId if the business data is empty" in {
      hasBusinessPartnerRecordWithEmptyBusinessData(nino, mtdItId1)

      val response = await(connector.getBusinessDetails(nino)).get

      response.businessData.length shouldBe 0
      response.mtdbsa shouldBe Some(mtdItId1)
    }
  }

  "getVatCustomerInformation" should {
    val clientVrn = Vrn("101747641")

    "return VatCustomerInformation for a subscribed VAT customer" when {

      "effectiveRegistrationDate is present" in {
        hasVatCustomerDetails(clientVrn, Some("2017-04-01"))

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe Some(LocalDate.parse("2017-04-01"))
        vatCustomerInfo.isInsolvent shouldBe false
      }

      "effectiveRegistrationDate is present and customer is insolvent" in {
        hasVatCustomerDetails(clientVrn, Some("2017-04-01"), true)

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe Some(LocalDate.parse("2017-04-01"))
        vatCustomerInfo.isInsolvent shouldBe true
      }

      "effectiveRegistrationDate is not present" in {
        hasVatCustomerDetails(clientVrn, None)

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe None
      }

      "there is no approvedInformation" in {
        hasVatCustomerDetailsWithNoApprovedInformation(clientVrn)

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe None
      }
    }

    "return None if the VAT client is not subscribed (404)" in {
      hasNoVatCustomerDetails(clientVrn)

      await(connector.getVatDetails(clientVrn)) shouldBe None
    }

    "record metrics for each call" in {
      hasVatCustomerDetails(clientVrn, Some("2017-04-01"))

      await(connector.getVatDetails(clientVrn)).get
      await(connector.getVatDetails(clientVrn))

      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-GetVatCustomerInformation-GET")
    }

    "throw UpstreamErrorResponse if DES is unavailable" in {
      failsVatCustomerDetails(clientVrn, withStatus = 502)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatDetails(clientVrn))
      }
    }

    "throw UpstreamErrorResponse if DES returns a 400 status (invalid vrn passed by client)" in {
      failsVatCustomerDetails(clientVrn, withStatus = 400)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatDetails(clientVrn))
      }
    }

    "throw UpstreamErrorResponse if DES returns a 403 status (MIGRATION)" in {
      failsVatCustomerDetails(clientVrn, withStatus = 403)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatDetails(clientVrn))
      }
    }
  }

  "GetTrustName" should {
    "return the name of a trust for a given Utr" in {

      getTrustName(utr.value, 200, """{"trustDetails": {"trustName": "Nelson James Trust"}}""")

      val result = await(connector.getTrustName(utr.value))
      result shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getTrustName-GET")
    }

    "return InvalidTrust response when 400" in {

      getTrustName(utr.value, 400,  """{"code": "INVALID_TRUST_STATE","reason": "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible."}""")

      val result = await(connector.getTrustName(utr.value))
      result shouldBe TrustResponse(Left(InvalidTrust("INVALID_TRUST_STATE", "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible.")))
    }
  }

  "GetAgencyDetails" should {
    "return agency details for a given ARN" in {

      givenDESRespondsWithValidData(Arn(arn), "My Agency Ltd")

      val result = await(connector.getAgencyDetails(Arn(arn)))
      result shouldBe Some(
        AgentDetailsDesResponse(Some(
          AgencyDetails(Some("My Agency Ltd"),
            Some("abc@xyz.com"),
            Some(BusinessAddress("Matheson House",
              Some("Grange Central"),
              Some("Town Centre"),
              Some("Telford"),
              Some("TF3 4ER"),
              "GB")))),
          Some(SuspensionDetails(
            false,
            None))))
    }

    "return None when DES responds with invalid data" in {
      givenDESRespondsWithoutValidData(Arn(arn))

      val result = await(connector.getAgencyDetails(Arn(arn)))

      result shouldBe Some(AgentDetailsDesResponse(None, None))
    }
  }

  "getPPTSubscription" should {
    "return a PPT subscription record" in {
      givenPptSubscription(PptRef("XAPPT1234567890"), true, true, false)
      val result = connector.getPptSubscription(PptRef("XAPPT1234567890")).futureValue
      result shouldBe Some(PptSubscription("Bill Sikes", LocalDate.parse("2021-10-12"), Some(LocalDate.parse("2050-10-01"))))
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-GetPptSubscriptionDisplay-GET")
    }
    "return None when IF responds with 4xx" in {
      givenPptSubscriptionRespondsWith(PptRef("XAPPT1234567890"), 404)
      val result = connector.getPptSubscription(PptRef("XAPPT1234567890")).futureValue
      result shouldBe None
    }

    "throw an exception when IF responds with 5xx" in {
      givenPptSubscriptionRespondsWith(PptRef("XAPPT1234567890"), 503)
      assertThrows[UpstreamErrorResponse]{
        await(connector.getPptSubscription(PptRef("XAPPT1234567890")))
      }
    }
  }

  "getTradingNameForNino" should {
      "return tradingName if defined in businessData" in {
        hasABusinessPartnerRecord(nino)
        val result = connector.getTradingNameForNino(nino).futureValue
        result shouldBe Some("Surname DADTN")
      }
    "return None when no businessData is defined" in {
      hasBusinessPartnerRecordWithNoBusinessData(nino)
      val result = connector.getTradingNameForNino(nino).futureValue
      result shouldBe None
    }
    "return None when no tradingName is defined within businessData" in {
      hasABusinessPartnerRecordWithBusinessDataWithNoTradingName(nino)
      val result = connector.getTradingNameForNino(nino).futureValue
      result shouldBe None
    }

    "return None when the Nino is not found" in {
      hasNoBusinessPartnerRecord(nino)
      val result = connector.getTradingNameForNino(nino).futureValue
      result shouldBe None
    }

    "throw an exception when DES returns a 5xx response" in {
      assertThrows[UpstreamErrorResponse]{
        businessPartnerRecordFails(nino, 503)
        await(connector.getTradingNameForNino(nino))
      }
    }
  }

  private def connector = app.injector.instanceOf[DesConnector]
}
