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

import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.{AgencyEmailNotFound, AgencyNameNotFound}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.service.ClientNameNotFound
import uk.gov.hmrc.agentclientauthorisation.support.PlatformAnalyticsStubs

class AgentCreateInvitationISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepository])
  lazy val controller = app.injector.instanceOf(classOf[AgencyInvitationsController])

  "POST /agencies/:arn/invitations/sent" should {
    val request = FakeRequest("POST", "/agencies/:arn/invitations/sent")

    "return 201 Created with link to invitation in headers" when {
      "service is ITSA" in {
        givenAuditConnector()
        givenTradingName(nino, "Trade Pears")
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenNinoForMtdItId(mtdItId, nino)

        givenAuthorisedAsAgent(arn)
        hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse(
          """{
            |  "service": "HMRC-MTD-IT",
            |  "clientIdType": "ni",
            |  "clientId": "AB123456A"
            |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is PIR" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenCitizenDetailsAreKnownFor(nino.value, "19122019")
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenPlatformAnalyticsRequestSent(true)

        val
        requestBody = Json.parse(
          """{
            |  "service": "PERSONAL-INCOME-RECORD",
            |  "clientIdType": "ni",
            |  "clientId": "AB123456A"
            |}""".stripMargin)

        val

        response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is VAT" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenClientDetailsForVat(vrn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse(
          """{
            |  "service": "HMRC-MTD-VAT",
            |  "clientIdType": "vrn",
            |  "clientId": "101747696"
            |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is Trust" in {
        val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        getTrustName(utr, response = trustNameJson)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse(
          """{
            |  "service": "HMRC-TERS-ORG",
            |  "clientIdType": "utr",
            |  "clientId": "2134514321"
            |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is CapitalGains" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse(
          s"""
             |{
             |  "service":"HMRC-CGT-PD",
             |  "clientIdType":"CGTPDRef",
             |  "clientId":"XMCGTP123456789"
             |}
           """.stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }
    }

    "throw exception when adding DetailsForEmail Failed" when {
      "Agency Email not found" in {
        givenAuditConnector()
        givenGetAgencyDetailsStub(arn, Some("name"), None)
        givenNinoForMtdItId(mtdItId, nino)

        givenAuthorisedAsAgent(arn)
        hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)

        val requestBody = Json.parse(
          """{
            |  "service": "HMRC-MTD-IT",
            |  "clientIdType": "ni",
            |  "clientId": "AB123456A"
            |}""".stripMargin)

        intercept[AgencyEmailNotFound] {
          await(controller.createInvitation(arn)(request.withJsonBody(requestBody)))
        }
      }

      "Agency Name not found" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, None, Some("email"))

        val
        requestBody = Json.parse(
          """{
            |  "service": "PERSONAL-INCOME-RECORD",
            |  "clientIdType": "ni",
            |  "clientId": "AB123456A"
            |}""".stripMargin)

        intercept[AgencyNameNotFound] {
          await(controller.createInvitation(arn)(request.withJsonBody(requestBody)))
        }
      }
      "Client Name not found" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))

        val requestBody = Json.parse(
          """{
            |  "service": "HMRC-MTD-VAT",
            |  "clientIdType": "vrn",
            |  "clientId": "101747696"
            |}""".stripMargin)


        intercept[ClientNameNotFound] {
          await(controller.createInvitation(arn)(request.withJsonBody(requestBody)))
        }
      }
    }

    "return BadRequest when the payload is invalid" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val requestBody = Json.parse(
        """{
          |  "foo": "HMRC-MTD-VAT",
          |  "bar": "vrn",
          |  "daa": "101747696"
          |}""".
          stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 400
    }

    "return 403 when arn is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)

      val requestBody = Json.parse(
        """{
          |  "service": "HMRC-MTD-IT",
          |  "clientIdType": "ni",
          |  "clientId": "AB123456A"
          |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 403
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenClientMtdItId(mtdItId)

      val requestBody = Json.parse(
        """{
          |  "service": "HMRC-MTD-IT",
          |  "clientIdType": "ni",
          |  "clientId": "AB123456A"
          |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 401
    }

    "return 403 when client registration is not found" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoBusinessPartnerRecord(nino)

      val
      requestBody = Json.parse(
        """{
          |  "service": "HMRC-MTD-IT",
          |  "clientIdType": "ni",
          |  "clientId": "AB123456A"
          |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 403
    }

    "return 403 when post code does not match" in {}

    "return 501 when service is unsupported" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val requestBody = Json.parse(
        """{
          |  "service": "FOO",
          |  "clientIdType": "ni",
          |  "clientId": "AB123456A"
          |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 501
    }
  }
}
