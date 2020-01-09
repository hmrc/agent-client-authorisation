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

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.{Scenario, StubMapping}
import play.utils.UriEncoding
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, CgtRef, MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

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

  def getCgtSubscription(cgtRef: CgtRef, status: Int = 200, response: String) = {
    stubFor(
      get(urlEqualTo(s"/subscriptions/CGT/ZCGT/${cgtRef.value}"))
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

  def givenDESRespondsWithValidData(identifier: TaxIdentifier, agencyName: String): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/registration/personal-details/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(personalDetailsResponseBodyWithValidData(agencyName))))

  def givenDESRespondsWithoutValidData(identifier: TaxIdentifier): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/registration/personal-details/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(personalDetailsResponseBodyWithoutValidData)))

  def personalDetailsResponseBodyWithValidData(agencyName: String) =
    s"""
       |{
       |   "isAnOrganisation" : true,
       |   "contactDetails" : {
       |      "phoneNumber" : "07000000000"
       |   },
       |   "isAnAgent" : true,
       |   "safeId" : "XB0000100101711",
       |   "agencyDetails" : {
       |      "agencyAddress" : {
       |         "addressLine2" : "Grange Central",
       |         "addressLine3" : "Town Centre",
       |         "addressLine4" : "Telford",
       |         "postalCode" : "TF3 4ER",
       |         "countryCode" : "GB",
       |         "addressLine1" : "Matheson House"
       |      },
       |      "agencyName" : "$agencyName",
       |      "agencyEmail" : "abc@xyz.com"
       |   },
       |   "suspensionDetails": {
       |     "suspensionStatus": true,
       |     "regimes": [
       |       "ITSA",
       |       "VATC"
       |     ]
       |   },
       |   "organisation" : {
       |      "organisationName" : "CT AGENT 183",
       |      "isAGroup" : false,
       |      "organisationType" : "0000"
       |   },
       |   "addressDetails" : {
       |      "addressLine2" : "Grange Central 183",
       |      "addressLine3" : "Telford 183",
       |      "addressLine4" : "Shropshire 183",
       |      "postalCode" : "TF3 4ER",
       |      "countryCode" : "GB",
       |      "addressLine1" : "Matheson House 183"
       |   },
       |   "individual" : {
       |      "firstName" : "John",
       |      "lastName" : "Smith"
       |   },
       |   "isAnASAgent" : true,
       |   "isAnIndividual" : false,
       |   "businessPartnerExists" : true,
       |   "agentReferenceNumber" : "TestARN"
       |}
            """.stripMargin

  val personalDetailsResponseBodyWithoutValidData =
    s"""
       |{
       |   "isAnOrganisation" : true,
       |   "contactDetails" : {
       |      "phoneNumber" : "07000000000"
       |   },
       |   "isAnAgent" : false,
       |   "safeId" : "XB0000100101711",
       |   "organisation" : {
       |      "organisationName" : "CT AGENT 183",
       |      "isAGroup" : false,
       |      "organisationType" : "0000"
       |   },
       |   "addressDetails" : {
       |      "addressLine2" : "Grange Central 183",
       |      "addressLine3" : "Telford 183",
       |      "addressLine4" : "Shropshire 183",
       |      "postalCode" : "TF3 4ER",
       |      "countryCode" : "GB",
       |      "addressLine1" : "Matheson House 183"
       |   },
       |   "isAnASAgent" : false,
       |   "isAnIndividual" : false,
       |   "businessPartnerExists" : true
       |}
            """.stripMargin


  val failureResponseBody =
    """
      |{
      |   "code" : "SOME_FAILURE",
      |   "reason" : "Some reason"
      |}
    """.stripMargin

  def givenDESReturnsError(
                            identifier: TaxIdentifier,
                            responseCode: Int,
                            errorMessage: String = failureResponseBody): StubMapping =
    stubFor(
      get(urlEqualTo(
        s"/registration/personal-details/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .inScenario("DES failure")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse()
          .withStatus(responseCode)
          .withBody(errorMessage)))


  def givenNinoIsUnknownFor(mtdbsa: MtdItId) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/mtdbsa/${mtdbsa.value}"))
        .willReturn(aResponse().withStatus(404)))

  def givenMtdItIdIsUnknownFor(nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .willReturn(aResponse().withStatus(404)))

  def givenNinoIsKnownFor(mtdbsa: MtdItId, nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/mtdbsa/${mtdbsa.value}"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{ "nino": "${nino.value}" }""")))

  def givenMtdItIdIsKnownFor(nino: Nino, mtdItId: MtdItId) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{ "mtdbsa": "${mtdItId.value}" }""")))

  def givenTradingNameIsKnownFor(nino: Nino, tradingName: String) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{
    "safeId" : "XE00001234567890",
    "nino" : "AA123456A",
    "mtdbsa" : "123456789012345",
    "propertyIncome" : false,
    "businessData" : [
        {
            "incomeSourceId" : "123456789012345",
            "accountingPeriodStartDate" : "2001-01-01",
            "accountingPeriodEndDate" : "2001-01-01",
            "tradingName" : "$tradingName",
            "businessAddressDetails" :
            {
                "addressLine1" : "100 SuttonStreet",
                "addressLine2" : "Wokingham",
                "addressLine3" : "Surrey",
                "addressLine4" : "London",
                "postalCode" : "DH14EJ",
                "countryCode" : "GB"
            },
            "businessContactDetails" :
            {
                "phoneNumber" : "01332752856",
                "mobileNumber" : "07782565326",
                "faxNumber" : "01332754256",
                "emailAddress" : "stephen@manncorpone.co.uk"
            },
            "tradingStartDate" : "2001-01-01",
            "cashOrAccruals" : "cash",
            "seasonal" : true,
            "cessationDate" : "2001-01-01",
            "cessationReason" : "002",
            "paperLess" : true
        }
    ]
}
""".stripMargin)))

  def givenNoTradingNameFor(nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{
    "businessData" : [
        {
            "incomeSourceId" : "123456789012345",
            "accountingPeriodStartDate" : "2001-01-01",
            "accountingPeriodEndDate" : "2001-01-01",
            "businessAddressDetails" :
            {
                "addressLine1" : "100 SuttonStreet",
                "addressLine2" : "Wokingham",
                "addressLine3" : "Surrey",
                "addressLine4" : "London",
                "postalCode" : "DH14EJ",
                "countryCode" : "GB"
            }
        }
    ]
}
""".stripMargin)))


  def givenDesReturnsServiceUnavailable() =
    stubFor(
      get(urlMatching(s"/registration/.*"))
        .willReturn(aResponse().withStatus(503)))

  def givenDESRespondsWithRegistrationData(identifier: TaxIdentifier, isIndividual: Boolean): StubMapping =
    stubFor(
      post(urlEqualTo(s"/registration/individual/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(registrationData(isIndividual))))

  def givenDESRespondsWithoutRegistrationData(identifier: TaxIdentifier): StubMapping =
    stubFor(
      post(urlEqualTo(s"/registration/individual/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(invalidRegistrationData)))

  def givenDESReturnsErrorForRegistration(
                                           identifier: TaxIdentifier,
                                           responseCode: Int,
                                           errorMessage: String = failureResponseBody): StubMapping =
    stubFor(
      post(urlEqualTo(s"/registration/individual/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .inScenario("DES failure")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse()
          .withStatus(responseCode)
          .withBody(errorMessage)))

  def givenDESReturnsErrorFirstAndValidDataLater(
                                                  identifier: TaxIdentifier,
                                                  isIndividual: Boolean,
                                                  responseCode: Int): StubMapping = {
    stubFor(
      post(urlEqualTo(s"/registration/individual/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .inScenario("Retry")
        .whenScenarioStateIs(Scenario.STARTED)
        .willReturn(aResponse()
          .withStatus(responseCode)
          .withBody(failureResponseBody))
        .willSetStateTo("DES Failure #2"))
    stubFor(
      post(urlEqualTo(s"/registration/individual/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .inScenario("Retry")
        .whenScenarioStateIs("DES Failure #2")
        .willReturn(aResponse()
          .withStatus(responseCode)
          .withBody(failureResponseBody))
        .willSetStateTo("DES Success"))
    stubFor(
      post(urlEqualTo(s"/registration/individual/${identifier.getClass.getSimpleName.toLowerCase}/${identifier.value}"))
        .inScenario("Retry")
        .whenScenarioStateIs("DES Success")
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(registrationData(isIndividual)))
        .willSetStateTo(Scenario.STARTED))
  }

  private def registrationData(isIndividual: Boolean) =
    if (isIndividual) registrationDataForIndividual else registrationDataForOrganisation


  val registrationDataForOrganisation =
    s"""
       |{
       |   "contactDetails" : {},
       |   "organisation" : {
       |      "organisationName" : "CT AGENT 165",
       |      "organisationType" : "Not Specified",
       |      "isAGroup" : false
       |   },
       |   "address" : {
       |      "addressLine1" : "Matheson House 165",
       |      "countryCode" : "GB",
       |      "addressLine2" : "Grange Central 165",
       |      "addressLine4" : "Shropshire 165",
       |      "addressLine3" : "Telford 165",
       |      "postalCode" : "TF3 4ER"
       |   },
       |   "isEditable" : false,
       |   "isAnAgent" : true,
       |   "safeId" : "XH0000100100761",
       |   "agentReferenceNumber" : "SARN0001028",
       |   "isAnASAgent" : true,
       |   "isAnIndividual" : false,
       |   "sapNumber" : "0100100761"
       |}
     """.stripMargin

  val registrationDataForIndividual =
    s"""
       |{
       |   "isAnIndividual" : true,
       |   "isAnASAgent" : true,
       |   "isEditable" : false,
       |   "isAnAgent" : true,
       |   "contactDetails" : {},
       |   "safeId" : "XR0000100115180",
       |   "agentReferenceNumber" : "PARN0002156",
       |   "individual" : {
       |      "firstName" : "First Name QM",
       |      "dateOfBirth" : "1992-05-10",
       |      "lastName" : "Last Name QM"
       |   },
       |   "address" : {
       |      "postalCode" : "TF3 4ER",
       |      "addressLine4" : "AddressFour 190",
       |      "addressLine2" : "AddressTwo 190",
       |      "addressLine1" : "AddressOne 190",
       |      "addressLine3" : "AddressThree 190",
       |      "countryCode" : "GB"
       |   },
       |   "sapNumber" : "0100115180"
       |}
     """.stripMargin

  val invalidRegistrationData =
    s"""
       |{
       |   "isAnIndividual" : true,
       |   "isAnASAgent" : true,
       |   "isEditable" : false,
       |   "isAnAgent" : true,
       |   "contactDetails" : {},
       |   "safeId" : "XR0000100115180",
       |   "agentReferenceNumber" : "PARN0002156",
       |   "address" : {
       |      "postalCode" : "TF3 4ER",
       |      "addressLine4" : "AddressFour 190",
       |      "addressLine2" : "AddressTwo 190",
       |      "addressLine1" : "AddressOne 190",
       |      "addressLine3" : "AddressThree 190",
       |      "countryCode" : "GB"
       |   },
       |   "sapNumber" : "0100115180"
       |}
     """.stripMargin


  def givenCustomerDetailsKnownFor(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{
                                                     "approvedInformation" :
    {
        "customerDetails" :
        {
            "organisationName" : "McDonalds CORP",
            "individual" :
            {
                "title" : "0001",
                "firstName" : "Ronald",
                "middleName" : "A",
                "lastName" : "McDonald"
            },
            "tradingName" : "MCD",
            "mandationStatus" : "1",
            "registrationReason" : "0001",
            "effectiveRegistrationDate" : "1967-08-13",
            "businessStartDate" : "1967-08-13"
        }}}""".stripMargin)))

  def givenCustomerDetailsWithoutIndividual(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .willReturn(aResponse().withStatus(200).withBody(s"""{
                                                     "approvedInformation" :
    {
        "customerDetails" :
        {
            "organisationName" : "McDonalds CORP",
            "tradingName" : "MCD",
            "mandationStatus" : "1",
            "registrationReason" : "0001",
            "effectiveRegistrationDate" : "1967-08-13",
            "businessStartDate" : "1967-08-13"
        }}}""".stripMargin)))

  def givenCustomerDetailsWithoutOrganisation(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .willReturn(aResponse().withStatus(200).withBody(s"""|{
                                                             |"approvedInformation" : {
                                                             |     "customerDetails" : {
                                                             |         "individual" : {
                                                             |             "title" : "0001",
                                                             |             "firstName" : "Ronald",
                                                             |             "middleName" : "A",
                                                             |             "lastName" : "McDonald"
                                                             |         },
                                                             |         "tradingName" : "MCD",
                                                             |         "mandationStatus" : "1",
                                                             |         "registrationReason" : "0001",
                                                             |         "effectiveRegistrationDate" : "1967-08-13",
                                                             |         "businessStartDate" : "1967-08-13"
                                                             |         }
                                                             	|}
                                                             |}""".stripMargin)))

  def givenNoCustomerDetails(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .willReturn(aResponse().withStatus(200).withBody(s"""|{
                                                             |    "approvedInformation" :{}
                                                             |}""".stripMargin)))


  def givenGetAgencyDetailsStub(arn: Arn, agentName: Option[String] = None, agentEmail: Option[String] = None) = {
    val body = s"""
                  | {
                  | "agencyDetails" : {
                  |      "agencyAddress" : {
                  |         "addressLine2" : "Grange Central",
                  |         "addressLine3" : "Town Centre",
                  |         "addressLine4" : "Telford",
                  |         "postalCode" : "TF3 4ER",
                  |         "countryCode" : "GB",
                  |         "addressLine1" : "Matheson House"
                  |      }
                  | ${agentName.map(name => s""","agencyName" : "$name"""").getOrElse("")}
                  | ${agentEmail.map(email => s""","agencyEmail" : "$email"""").getOrElse("")}
                  |}
                  |}""".stripMargin

    stubFor(
      get(urlEqualTo(s"/registration/personal-details/arn/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(body)))
  }

  def givenAgencyNameNotFoundClientStub(arn: Arn) =
    stubFor(
      get(urlEqualTo(s"/registration/personal-details/arn/${encodePathSegment(arn.value)}"))
        .willReturn(aResponse()
          .withStatus(404)))

  def givenAgencyNameNotFoundAgentStub(arn: Arn) =
    stubFor(
      get(urlEqualTo(s"/registration/personal-details/arn/${encodePathSegment(arn.value)}"))
        .willReturn(aResponse()
          .withStatus(404)))

  def givenTradingName(nino: Nino, tradingName: String) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"tradingName": "$tradingName"}""")
        ))

  def givenTradingNameMissing(nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{}""")
        ))

  def givenTradingNameNotFound(nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${nino.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        ))

  def givenClientDetailsForVat(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/vat/customer/vrn/${vrn.value}/information"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                "approvedInformation": {
                  "customerDetails": {
                    "organisationName": "McDonalds CORP",
                    "individual": {
                      "title": "0001",
                      "firstName": "Ronald",
                      "middleName": "A",
                      "lastName": "McDonald"
                    },
                    "tradingName": "MCD",
                    "mandationStatus": "1",
                    "registrationReason": "0001",
                    "effectiveRegistrationDate": "1967-08-13",
                    "businessStartDate": "1967-08-13"
                  }
                }
              }""".stripMargin)))

  def givenNinoForMtdItId(mtdItId: MtdItId, nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/mtdbsa/${mtdItId.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         | {
                         |    "nino":"${nino.value}"
                         | }
               """.stripMargin)
        )
    )
  def givenNinoNotFoundForMtdItId(mtdItId: MtdItId) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/mtdbsa/${mtdItId.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )

  private def encodePathSegment(pathSegment: String): String =
    UriEncoding.encodePathSegment(pathSegment, UTF_8.name)
}
