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

import java.net.URL

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, Vrn}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

trait DesStubs[A] {
  me: A with WiremockAware =>

  def clientId: ClientId

  def hasABusinessPartnerRecord(postcode: String = "AA11AA",
                                countryCode: String = "GB"): A = {
    stubFor(get(urlEqualTo(
      s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
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

  def hasABusinessPartnerRecordWithMtdItId(mtdItId: MtdItId = mtdItId1): A = {
    stubFor(get(urlEqualTo(
      s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
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

  def hasBusinessPartnerRecordWithEmptyBusinessData(
      mtdItId: MtdItId = mtdItId1): A = {
    stubFor(get(urlEqualTo(
      s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
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

  def hasNoBusinessPartnerRecord: A = {
    stubFor(get(urlEqualTo(
      s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
      .withHeader("authorization", equalTo("Bearer secret"))
      .withHeader("environment", equalTo("test"))
      .willReturn(aResponse()
        .withStatus(404)))

    this
  }

  def hasVatCustomerDetails(isEffectiveRegistrationDatePresent: Boolean): A = {
    stubFor(
      get(urlEqualTo(
        s"/vat/customer/vrn/${encodePathSegment(clientId.value)}/information"))
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
                         ""","effectiveRegistrationDate" : "2017-04-01""""
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

  def hasVatCustomerDetailsWithNoApprovedInformation: A = {
    stubFor(
      get(urlEqualTo(
        s"/vat/customer/vrn/${encodePathSegment(clientId.value)}/information"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody("{}")))

    this
  }

  def hasNoVatCustomerDetails: A = {
    stubFor(
      get(urlEqualTo(
        s"/vat/customer/vrn/${encodePathSegment(clientId.value)}/information"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(404)))

    this
  }

  def failsVatCustomerDetails(withStatus: Int): A = {
    stubFor(
      get(urlEqualTo(
        s"/vat/customer/vrn/${encodePathSegment(clientId.value)}/information"))
        .willReturn(aResponse()
          .withStatus(withStatus)))

    this
  }
}
