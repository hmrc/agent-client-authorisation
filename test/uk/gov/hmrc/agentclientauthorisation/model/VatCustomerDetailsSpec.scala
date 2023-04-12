/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

class VatCustomerDetailsSpec extends UnitSpec {

  def json(title: String) = s"""{
    "approvedInformation" :
    {
        "customerDetails" :
        {
            "organisationName" : "Ancient Antiques",
            "individual" :
            {
                "title" : "$title",
                "firstName" : "Fred",
                "middleName" : "M",
                "lastName" : "Flintstone"
            },
            "tradingName" : "a",
            "mandationStatus" : "1",
            "registrationReason" : "0001",
            "effectiveRegistrationDate" : "1967-08-13",
            "businessStartDate" : "1967-08-13"
        },
        "PPOB" :
        {
            "address" :
            {
                "line1" : "VAT ADDR 1",
                "line2" : "VAT ADDR 2",
                "line3" : "VAT ADDR 3",
                "line4" : "VAT ADDR 4",
                "postCode" : "SW1A 2BQ",
                "countryCode" : "ES"
            },
            "RLS" : "0001",
            "contactDetails" :
            {
                "primaryPhoneNumber" : "01257162661",
                "mobileNumber" : "07128126712 ",
                "faxNumber" : "01268712671 ",
                "emailAddress" : "antiques@email.com"
            }
        },
        "correspondenceContactDetails" :
        {
            "address" :
            {
                "line1" : "VAT ADDR 1",
                "line2" : "VAT ADDR 2",
                "line3" : "VAT ADDR 3",
                "line4" : "VAT ADDR 4",
                "postCode" : "SW1A 2BQ",
                "countryCode" : "ES"
            },
            "RLS" : "0001",
            "contactDetails" :
            {
                "primaryPhoneNumber" : "01257162661",
                "mobileNumber" : "07128126712",
                "faxNumber" : "01268712671",
                "emailAddress" : "antiques@email.com"
            }
        },
        "bankDetails" :
        {
            "IBAN" : "a",
            "BIC" : "a",
            "accountHolderName" : "Flintstone Quarry",
            "bankAccountNumber" : "00012345",
            "sortCode" : "010103",
            "buildingSocietyNumber" : "12312345",
            "bankBuildSocietyName" : "a"
        },
        "businessActivities" :
        {
            "primaryMainCode" : "00000",
            "mainCode2" : "00000",
            "mainCode3" : "00000",
            "mainCode4" : "00000"
        },
        "flatRateScheme" :
        {
            "FRSCategory" : "001",
            "FRSPercentage" : 123.12,
            "limitedCostTrader" : true,
            "startDate" : "2001-01-01"
        },
        "deregistration" :
        {
            "deregistrationReason" : "0001",
            "effectDateOfCancellation" : "2001-01-01",
            "lastReturnDueDate" : "2001-01-01"
        },
        "returnPeriod" :
        {
            "stdReturnPeriod" : "MA",
            "nonStdTaxPeriods" :
            {
                "period01" : "2001-01-01",
                "period02" : "2001-01-01",
                "period03" : "2001-01-01",
                "period04" : "2001-01-01",
                "period05" : "2001-01-01",
                "period06" : "2001-01-01",
                "period07" : "2001-01-01",
                "period08" : "2001-01-01",
                "period09" : "2001-01-01",
                "period10" : "2001-01-01",
                "period11" : "2001-01-01",
                "period12" : "2001-01-01",
                "period13" : "2001-01-01",
                "period14" : "2001-01-01",
                "period15" : "2001-01-01",
                "period16" : "2001-01-01",
                "period17" : "2001-01-01",
                "period18" : "2001-01-01",
                "period19" : "2001-01-01",
                "period20" : "2001-01-01",
                "period21" : "2001-01-01",
                "period22" : "2001-01-01",
                "period23" : "2001-01-01",
                "period24" : "2001-01-01"
            }
        },
        "groupOrPartnerMbrs" : [
            {
                "typeOfRelationship" : "01",
                "organisationName" : "abcdefghijklmn",
                "individual" :
                {
                    "title" : "0001",
                    "firstName" : "abcdefghijklmnopq",
                    "middleName" : "abcdefg",
                    "lastName" : "abcdefghijklm"
                },
                "SAP_Number" : "012345678901234567890123456789012345678912"
            }
        ]
    },
    "inFlightInformation" :
    {
        "changeIndicators" :
        {
            "customerDetails" : true,
            "PPOBDetails" : false,
            "correspContactDetails" : false,
            "bankDetails" : true,
            "businessActivities" : true,
            "flatRateScheme" : false,
            "deRegistrationInfo" : false,
            "returnPeriods" : true,
            "groupOrPartners" : true
        },
        "inflightChanges" :
        {
            "customerDetails" :
            {
                "formInformation" :
                {
                    "formBundle" : "012345678912",
                    "dateReceived" : "2001-01-01"
                },
                "organisationName" : "Ancient Antiques",
                "individual" :
                {
                    "title" : "0001",
                    "firstName" : "Fred",
                    "middleName" : "M",
                    "lastName" : "Flintstone"
                },
                "tradingName" : "a",
                "mandationStatus" : "1",
                "registrationReason" : "0001",
                "effectiveRegistrationDate" : "1967-08-13"
            },
            "PPOBDetails" :
            {
                "formInformation" :
                {
                    "formBundle" : "012345678912",
                    "dateReceived" : "2001-01-01"
                },
                "address" :
                {
                    "line1" : "VAT ADDR 1",
                    "line2" : "VAT ADDR 2",
                    "line3" : "VAT ADDR 3",
                    "line4" : "VAT ADDR 4",
                    "postCode" : "SW1A 2BQ",
                    "countryCode" : "ES"
                },
                "contactDetails" :
                {
                    "primaryPhoneNumber" : "01257162661",
                    "mobileNumber" : "07128126712",
                    "faxNumber" : "01268712671",
                    "emailAddress" : "antiques@email.com"
                },
                "websiteAddress" : "abcdefghijklmn"
            },
            "correspondenceContactDetails" :
            {
                "formInformation" :
                {
                    "formBundle" : "012345678912",
                    "dateReceived" : "2001-01-01"
                },
                "address" :
                {
                    "line1" : "VAT ADDR 1",
                    "line2" : "VAT ADDR 2",
                    "line3" : "VAT ADDR 3",
                    "line4" : "VAT ADDR 4",
                    "postCode" : "SW1A 2BQ",
                    "countryCode" : "ES"
                },
                "contactDetails" :
                {
                    "primaryPhoneNumber" : "01257162661",
                    "mobileNumber" : "07128126712",
                    "faxNumber" : "01268712671",
                    "emailAddress" : "antiques@email.com"
                }
            },
            "bankDetails" :
            {
                "formInformation" :
                {
                    "formBundle" : "012345678912",
                    "dateReceived" : "2001-01-01"
                },
                "IBAN" : "a",
                "BIC" : "a",
                "accountHolderName" : "Flintstone Quarry",
                "bankAccountNumber" : "00012345",
                "sortCode" : "010103",
                "buildingSocietyNumber" : "12312345",
                "bankBuildSocietyName" : "a"
            },
            "groupOrPartner" : [
                {
                    "formInformation" :
                    {
                        "formBundle" : "012345678912",
                        "dateReceived" : "2001-01-01"
                    },
                    "action" : "1",
                    "SAP_Number" : "012345678901234567890123456789012345678912",
                    "typeOfRelationship" : "01",
                    "makeGrpMember" : false,
                    "makeControllingBody" : false,
                    "isControllingBody" : false,
                    "organisationName" : "abcdefg",
                    "tradingName" : "abcdefghijkl",
                    "individual" :
                    {
                        "title" : "0001",
                        "firstName" : "abcdefghijk",
                        "middleName" : "abcdefghijklmno",
                        "lastName" : "abcdefg"
                    }
                }
            ],
            "deregister" :
            {
                "formInformation" :
                {
                    "formBundle" : "012345678912",
                    "dateReceived" : "2001-01-01"
                },
                "deregistrationReason" : "0001",
                "deregDate" : "2001-01-01",
                "deregDateInFuture" : "2001-01-01"
            },
            "returnPeriod" :
            {
                "formInformation" :
                {
                    "formBundle" : "012345678912",
                    "dateReceived" : "2001-01-01"
                },
                "changeReturnPeriod" : false,
                "nonStdTaxPeriodsRequested" : false,
                "ceaseNonStdTaxPeriods" : false,
                "stdReturnPeriod" : "MA",
                "nonStdTaxPeriods" :
                {
                    "period01" : "2001-01-01",
                    "period02" : "2001-01-01",
                    "period03" : "2001-01-01",
                    "period04" : "2001-01-01",
                    "period05" : "2001-01-01",
                    "period06" : "2001-01-01",
                    "period07" : "2001-01-01",
                    "period08" : "2001-01-01",
                    "period09" : "2001-01-01",
                    "period10" : "2001-01-01",
                    "period11" : "2001-01-01",
                    "period12" : "2001-01-01",
                    "period13" : "2001-01-01",
                    "period14" : "2001-01-01",
                    "period15" : "2001-01-01",
                    "period16" : "2001-01-01",
                    "period17" : "2001-01-01",
                    "period18" : "2001-01-01",
                    "period19" : "2001-01-01",
                    "period20" : "2001-01-01",
                    "period21" : "2001-01-01",
                    "period22" : "2001-01-01",
                    "period23" : "2001-01-01",
                    "period24" : "2001-01-01"
                }
            }
        }
    }
}""".stripMargin

  "CustomerDetails reads method" should {
    "successfully read the json and convert it to CustomerDetails type" in {
      val result: VatCustomerDetails =
        (Json.parse(json("0001")) \ "approvedInformation" \ "customerDetails").as[VatCustomerDetails]
      result.organisationName shouldBe Some("Ancient Antiques")
      result.individual shouldBe Some(VatIndividual(Some("Mr"), Some("Fred"), Some("M"), Some("Flintstone")))
      result.tradingName shouldBe Some("a")
    }

    "return json with no title when title in payload does not exisit" in {
      val result: VatCustomerDetails =
        (Json.parse(json("")) \ "approvedInformation" \ "customerDetails").as[VatCustomerDetails]
      result.organisationName shouldBe Some("Ancient Antiques")
      result.individual shouldBe Some(VatIndividual(None, Some("Fred"), Some("M"), Some("Flintstone")))
      result.tradingName shouldBe Some("a")
    }

    "return json with no title for unsupported title" in {
      val result: VatCustomerDetails =
        (Json.parse(json("Grand Old Duke of York")) \ "approvedInformation" \ "customerDetails").as[VatCustomerDetails]
      result.organisationName shouldBe Some("Ancient Antiques")
      result.individual shouldBe Some(VatIndividual(None, Some("Fred"), Some("M"), Some("Flintstone")))
      result.tradingName shouldBe Some("a")
    }
  }
}
