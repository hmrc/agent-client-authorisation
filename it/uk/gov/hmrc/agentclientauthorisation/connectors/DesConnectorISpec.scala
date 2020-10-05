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

import org.joda.time.LocalDate
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, DesStubs}
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.http.UpstreamErrorResponse
import uk.gov.hmrc.play.test.UnitSpec

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
        hasVatCustomerDetails(clientVrn, "2017-04-01", isEffectiveRegistrationDatePresent = true)

        val vatCustomerInfo = await(connector.getVatRegDate(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe Some(LocalDate.parse("2017-04-01"))
      }

      "effectiveRegistrationDate is not present" in {
        hasVatCustomerDetails(clientVrn, "2017-04-01", isEffectiveRegistrationDatePresent = false)

        val vatCustomerInfo = await(connector.getVatRegDate(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe None
      }

      "there is no approvedInformation" in {
        hasVatCustomerDetailsWithNoApprovedInformation(clientVrn)

        val vatCustomerInfo = await(connector.getVatRegDate(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe None
      }
    }

    "return None if the VAT client is not subscribed" in {
      hasNoVatCustomerDetails(clientVrn)

      await(connector.getVatRegDate(clientVrn)) shouldBe None
    }

    "record metrics for each call" in {
      hasVatCustomerDetails(clientVrn, "2017-04-01", true)

      await(connector.getVatRegDate(clientVrn)).get
      await(connector.getVatRegDate(clientVrn))

      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-GetVatCustomerInformation-GET")
    }

    "throw Upstream5xxResponse if DES is unavailable" in {
      failsVatCustomerDetails(clientVrn, withStatus = 502)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatRegDate(clientVrn))
      }
    }
  }

  private def connector = app.injector.instanceOf[DesConnector]

}
