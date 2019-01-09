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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentCreateInvitationISpec
    extends UnitSpec with MongoAppAndStubs with AuthStubs with DesStubs with HttpResponseUtils {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepository])
  lazy val controller = app.injector.instanceOf(classOf[AgencyInvitationsController])

  val arn = "TARN0000001"
  val mtdItId = "YNIZ22082177289"
  val nino = "AB123456A"
  val vrn = "660304567"
  val utr = "9246624558"

  "POST /agencies/:arn/invitations/sent" should {
    "return 204 with invitation response if invitation creation is successful" when {
      "service is ITSA" in {

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenBusinessPartnerRecordWithMtdItId(mtdItId, nino)

        val response: HttpResponse =
          new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent", port)
            .postAsJson(s"""
                           |{
                           |  "service": "HMRC-MTD-IT",
                           |  "clientIdType": "ni",
                           |  "clientId": "$nino"
                           |}
               """.stripMargin)
        response.status shouldBe 201
        locationHeaderOf(response) should (fullyMatch regex s"/agent-client-authorisation/agencies/$arn/invitations/sent/A\\w{12}")
      }

      "service is PIR" in {}

      "service is VAT" in {

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)

        val response: HttpResponse =
          new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent", port)
            .postAsJson(s"""
                           |{
                           |  "service": "HMRC-MTD-VAT",
                           |  "clientIdType": "vrn",
                           |  "clientId": "$vrn"
                           |}
               """.stripMargin)
        response.status shouldBe 201
        locationHeaderOf(response) should (fullyMatch regex s"/agent-client-authorisation/agencies/$arn/invitations/sent/C\\w{12}")
      }

      "service is NI-ORG" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)

        val response: HttpResponse =
          new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent", port)
            .postAsJson(s"""
                           |{
                           |  "service": "HMRC-NI-ORG",
                           |  "clientIdType": "utr",
                           |  "clientId": "$utr"
                           |}
               """.stripMargin)
        response.status shouldBe 201
        locationHeaderOf(response) should (fullyMatch regex s"/agent-client-authorisation/agencies/$arn/invitations/sent/D\\w{12}")

      }

    }

    "return 400 when JSON data is incorrect" in {}

    "return 400 when JSON is invalid" in {}

    "return 403 when client registration is not found" in {}

    "return 403 when post code does not match" in {}

    "return 501 when service is unsupported" in {}

    "return 401 when user is not an agent" in {}

  }
}

trait AuthStubs {

  def givenAuthorisedAsAgent(arn: String) =
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse()
        .withStatus(200)
        .withBody(
          s"""{"allEnrolments":[{"key":"HMRC-AS-AGENT","identifiers":[{"key":"AgentReferenceNumber","value":"$arn"}]}], "affinityGroup":"Agent"}""")))

}

trait DesStubs {

  def givenBusinessPartnerRecordWithMtdItId(mtdItId: String, nino: String) =
    stubFor(
      get(urlEqualTo(s"/registration/business-details/nino/${encodePathSegment(nino)}"))
        .withHeader("authorization", equalTo("Bearer secret"))
        .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
          .withBody(s"""
                       |  {
                       |  "safeId": "XV0000100093327",
                       |  "nino": "ZR987654C",
                       |  "mtdbsa": "$mtdItId",
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

}

trait HttpResponseUtils {

  def locationHeaderOf(response: HttpResponse): String =
    response.header(HeaderNames.LOCATION).getOrElse(throw new IllegalStateException())

}
