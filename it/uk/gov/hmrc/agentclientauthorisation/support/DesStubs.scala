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

package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

trait DesStubs {

  def hasABusinessPartnerRecord(nino: Nino, postcode: String = "AA11AA", countryCode: String = "GB") = {
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(s"""
                       |  {
                       |  "safeId": "XV0000100093327",
                       |  "nino": "ZR987654C",
                       |  "propertyIncome": false,
                       |  "businessData": [
                       |    {
                       |      "incomeSourceId": "XWIS00000000219",
                       |      "accountingPeriodStartDate": "2017-05-06",
                       |      "accountingPeriodEndDate": "2018-05-05",
                       |      "tradingName": "Surname DADTN",
                       |      "businessAddressDetails": {
                       |        "addressLine1": "100 Sutton Street",
                       |        "addressLine2": "Wokingham",
                       |        "addressLine3": "Surrey",
                       |        "addressLine4": "London",
                       |        "postalCode": "$postcode",
                       |        "countryCode": "$countryCode"
                       |      },
                       |      "businessContactDetails": {
                       |        "phoneNumber": "01111222333",
                       |        "mobileNumber": "04444555666",
                       |        "faxNumber": "07777888999",
                       |        "emailAddress": "aaa@aaa.com"
                       |      },
                       |      "tradingStartDate": "2016-05-06",
                       |      "cashOrAccruals": "cash",
                       |      "seasonal": true
                       |    }
                       |  ]
                       |}
              """.stripMargin)))
    this
  }

  def hasABusinessPartnerRecordWithMtdItId(nino: Nino, mtdItId: MtdItId = mtdItId1) = {
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(s"""
                       |  {
                       |  "safeId": "XV0000100093327",
                       |  "nino": "${nino.value}",
                       |  "mtdbsa": "${mtdItId.value}",
                       |  "propertyIncome": false,
                       |  "businessData": [
                       |    {
                       |      "incomeSourceId": "XWIS00000000219",
                       |      "accountingPeriodStartDate": "2017-05-06",
                       |      "accountingPeriodEndDate": "2018-05-05",
                       |      "tradingName": "Surname DADTN",
                       |      "businessAddressDetails": {
                       |        "addressLine1": "100 Sutton Street",
                       |        "addressLine2": "Wokingham",
                       |        "addressLine3": "Surrey",
                       |        "addressLine4": "London",
                       |        "postalCode": "AA11AA",
                       |        "countryCode": "GB"
                       |      },
                       |      "businessContactDetails": {
                       |        "phoneNumber": "01111222333",
                       |        "mobileNumber": "04444555666",
                       |        "faxNumber": "07777888999",
                       |        "emailAddress": "aaa@aaa.com"
                       |      },
                       |      "tradingStartDate": "2016-05-06",
                       |      "cashOrAccruals": "cash",
                       |      "seasonal": true
                       |    }
                       |  ]
                       |}
                       |""".stripMargin)))
    this
  }

  def hasBusinessPartnerRecordWithEmptyBusinessData(nino: Nino, mtdItId: MtdItId = mtdItId1) = {
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(s"""
                       |  {
                       |  "safeId": "XV0000100093327",
                       |  "nino": "ZR987654C",
                       |  "mtdbsa": "${mtdItId.value}",
                       |  "propertyIncome": false,
                       |  "businessData": []
                       |}
                       |""".stripMargin)))
    this
  }

  def hasNoBusinessPartnerRecord(nino: Nino) = {
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(404)))

    this
  }

  def hasVatCustomerDetails(vrn: Vrn, vatRegDate: String, isEffectiveRegistrationDatePresent: Boolean) = {
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(s"""{
                       |   "approvedInformation" : {
                       |      "customerDetails" : {
                       |         "organisationName" : " TAXPAYER NAME_1",
                       |         "mandationStatus" : "2",
                       |         "businessStartDate" : "2017-04-02",
                       |         "registrationReason" : "0013"
                       |         ${if (isEffectiveRegistrationDatePresent)
                         s""","effectiveRegistrationDate" : "$vatRegDate""""
                       else ""}
                       |      },
                       |      "bankDetails" : {
                       |         "sortCode" : "16****",
                       |         "accountHolderName" : "***********************",
                       |         "bankAccountNumber" : "****9584"
                       |      },
                       |      "deregistration" : {
                       |         "effectDateOfCancellation" : "2018-03-01",
                       |         "lastReturnDueDate" : "2018-02-24",
                       |         "deregistrationReason" : "0001"
                       |      },
                       |      "PPOB" : {
                       |         "address" : {
                       |            "line4" : "VAT PPOB Line5",
                       |            "postCode" : "HA0 3ET",
                       |            "countryCode" : "GB",
                       |            "line2" : "VAT PPOB Line2",
                       |            "line3" : "VAT PPOB Line3",
                       |            "line1" : "VAT PPOB Line1"
                       |         },
                       |         "contactDetails" : {
                       |            "mobileNumber" : "012345678902",
                       |            "emailAddress" : "testsignuptcooo37@hmrc.co.uk",
                       |            "faxNumber" : "012345678903",
                       |            "primaryPhoneNumber" : "012345678901"
                       |         },
                       |         "websiteAddress" : "www.tumbleweed.com"
                       |      },
                       |      "flatRateScheme" : {
                       |         "FRSCategory" : "003",
                       |         "limitedCostTrader" : true,
                       |         "FRSPercentage" : 59.99
                       |      },
                       |      "returnPeriod" : {
                       |         "stdReturnPeriod" : "MA"
                       |      },
                       |      "businessActivities" : {
                       |         "primaryMainCode" : "10410",
                       |         "mainCode3" : "10710",
                       |         "mainCode2" : "10611",
                       |         "mainCode4" : "10720"
                       |      }
                       |   }
                       |}""".stripMargin)))
    this
  }

  def hasVatCustomerDetailsWithNoApprovedInformation(vrn: Vrn) = {
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("{}")))

    this
  }

  def hasNoVatCustomerDetails(vrn: Vrn) = {
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(404)))

    this
  }

  def getTrustName(utr: Utr, status: Int = 200, response: String) = {
    stubFor(
      get(urlEqualTo(s"/trusts/agent-known-fact-check/${utr.value}"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody(response)))
  }

  def failsVatCustomerDetails(vrn: Vrn, withStatus: Int) = {
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .willReturn(aResponse()
          .withStatus(withStatus)))

    this
  }
}
