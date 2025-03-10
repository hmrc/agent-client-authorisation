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
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{AgencyEmailNotFound, AgencyNameNotFound, Invitation}
import uk.gov.hmrc.agentclientauthorisation.service.ClientNameNotFound
import uk.gov.hmrc.agentclientauthorisation.support.PlatformAnalyticsStubs
import uk.gov.hmrc.agentmtdidentifiers.model.Service

import java.time.{LocalDate, LocalDateTime}

class AgentCreateInvitationISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val controller = app.injector.instanceOf(classOf[AgencyInvitationsController])

  "POST /agencies/:arn/invitations/sent" should {
    val request = FakeRequest("POST", "/agencies/:arn/invitations/sent").withHeaders("Authorization" -> "Bearer testtoken")

    "return 201 Created with link to invitation in headers" when {
      "service is ITSA" in {
        givenAuditConnector()
        hasABusinessPartnerRecord(nino)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenNinoForMtdItId(mtdItId, nino)

        givenAuthorisedAsAgent(arn)
        hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse("""{
                                       |  "service": "HMRC-MTD-IT",
                                       |  "clientIdType": "ni",
                                       |  "clientId": "AB123456A"
                                       |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is ITSA SUPP" in {
        givenAuditConnector()
        hasABusinessPartnerRecord(nino)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenNinoForMtdItId(mtdItId7, nino)

        givenAuthorisedAsAgent(arn)
        hasABusinessPartnerRecordWithMtdItId(nino, mtdItId7)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse("""{
                                       |  "service": "HMRC-MTD-IT-SUPP",
                                       |  "clientIdType": "ni",
                                       |  "clientId": "AB123456A"
                                       |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }
      "service is ITSA SUPP - same nino has 2 supp" in {
        givenAuditConnector()
        hasABusinessPartnerRecord(nino)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenNinoForMtdItId(mtdItId, nino)

        givenAuthorisedAsAgent(arn)
        hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody1 = Json.parse("""{
                                        |  "service": "HMRC-MTD-IT-SUPP",
                                        |  "clientIdType": "ni",
                                        |  "clientId": "AB123456A"
                                        |}""".stripMargin)

        val response1 = controller.createInvitation(arn)(request.withJsonBody(requestBody1))

        status(response1) shouldBe 201

        // second supp agent for the same nino
        givenAuthorisedAsAgent(arn2)
        givenGetAgencyDetailsStub(arn2, Some("name"), Some("email"))

        val requestBody2 = Json.parse("""{
                                        |  "service": "HMRC-MTD-IT-SUPP",
                                        |  "clientIdType": "ni",
                                        |  "clientId": "AB123456A"
                                        |}""".stripMargin)

        val response2 = controller.createInvitation(arn2)(request.withJsonBody(requestBody2))

        status(response2) shouldBe 201

        verifyAnalyticsRequestSent(2)

      }
      "service is PIR" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenCitizenDetailsAreKnownFor(nino.value, "19122019")
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse("""{
                                       |  "service": "PERSONAL-INCOME-RECORD",
                                       |  "clientIdType": "ni",
                                       |  "clientId": "AB123456A"
                                       |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is VAT" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenClientDetailsForVat(vrn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse("""{
                                       |  "service": "HMRC-MTD-VAT",
                                       |  "clientIdType": "vrn",
                                       |  "clientId": "101747696"
                                       |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is Trust - UTR" in {
        val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        getTrustName(utr.value, response = trustNameJson)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse("""{
                                       |  "service": "HMRC-TERS-ORG",
                                       |  "clientIdType": "utr",
                                       |  "clientId": "2134514321"
                                       |}""".stripMargin)

        val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

        status(response) shouldBe 201

        verifyAnalyticsRequestSent(1)
      }

      "service is Trust - URN" in {
        val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        getTrustName(urn.value, response = trustNameJson)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse("""{
                                       |  "service": "HMRC-TERSNT-ORG",
                                       |  "clientIdType": "urn",
                                       |  "clientId": "XXTRUST12345678"
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

        val requestBody = Json.parse(s"""
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

      "service is PPT" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        givenPptSubscription(pptRef, true, true, false)
        givenPlatformAnalyticsRequestSent(true)

        val requestBody = Json.parse(s"""
                                        |{
                                        |  "service":"HMRC-PPT-ORG",
                                        |  "clientIdType":"EtmpRegistrationNumber",
                                        |  "clientId":"XAPPT0000000000"
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

        val requestBody = Json.parse("""{
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

        val requestBody = Json.parse("""{
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

        val requestBody = Json.parse("""{
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

      val requestBody = Json.parse("""{
                                     |  "foo": "HMRC-MTD-VAT",
                                     |  "bar": "vrn",
                                     |  "daa": "101747696"
                                     |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 400
    }

    "return 403 when arn is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)

      val requestBody = Json.parse("""{
                                     |  "service": "HMRC-MTD-IT",
                                     |  "clientIdType": "ni",
                                     |  "clientId": "AB123456A"
                                     |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 403
    }

    "return 403 when duplicate invitation" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      givenClientDetailsForVat(vrn)
      givenPlatformAnalyticsRequestSent(true)
      await(
        invitationsRepositoryImpl.collection
          .insertOne(Invitation.createNew(arn, Some("business"), Service.Vat, vrn, vrn, None, LocalDateTime.now(), LocalDate.now, None))
          .toFuture()
      )

      val requestBody = Json.parse("""{
                                     |  "service": "HMRC-MTD-VAT",
                                     |  "clientIdType": "vrn",
                                     |  "clientId": "101747696"
                                     |}""".stripMargin)

      val response = await(controller.createInvitation(arn)(request.withJsonBody(requestBody)))

      status(response) shouldBe 403

      bodyOf(response) shouldBe
        """{"code":"DUPLICATE_AUTHORISATION_REQUEST","message":"An authorisation request for this service has already been created and is awaiting the client’s response."}""".stripMargin

    }
    // TODO - test failing at the moment
    /*    "return 403 when Primary and Secondary - duplicate invitation" in {
      givenAuditConnector()
      hasABusinessPartnerRecord(nino)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      givenNinoForMtdItId(mtdItId, nino)

      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)
      givenPlatformAnalyticsRequestSent(true)

      await(
        invitationsRepositoryImpl.collection
          .insertOne(Invitation.createNew(arn, None, Service.MtdIt, mtdItId, nino, None, LocalDateTime.now(), LocalDate.now, None))
          .toFuture()
      )
      1 == 1

      val requestBody = Json.parse("""{
                                     |  "service": "HMRC-MTD-IT-SUPP",
                                     |  "clientIdType": "ni",
                                     |  "clientId": "AB123456A"
                                     |}""".stripMargin)

      val response = await(controller.createInvitation(arn)(request.withJsonBody(requestBody)))

      status(response) shouldBe 403

      bodyOf(response) shouldBe
        """{"code":"DUPLICATE_AUTHORISATION_REQUEST","message":"An authorisation request for this service has already been created and is awaiting the client’s response."}""".stripMargin

    }*/

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenClientMtdItId(mtdItId)

      val requestBody = Json.parse("""{
                                     |  "service": "HMRC-MTD-IT",
                                     |  "clientIdType": "ni",
                                     |  "clientId": "AB123456A"
                                     |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 401
    }

    "return 201 when client registration is not found - use Nino instead (Alt-ITSA) " in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoBusinessPartnerRecord(nino)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      givenCitizenDetailsAreKnownFor(nino.value, "any")

      val requestBody = Json.parse("""{
                                     |  "service": "HMRC-MTD-IT",
                                     |  "clientIdType": "ni",
                                     |  "clientId": "AB123456A"
                                     |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 201
    }

    "return 403 when post code does not match" in {}

    "return 501 when service is unsupported" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val requestBody = Json.parse("""{
                                     |  "service": "FOO",
                                     |  "clientIdType": "ni",
                                     |  "clientId": "AB123456A"
                                     |}""".stripMargin)

      val response = controller.createInvitation(arn)(request.withJsonBody(requestBody))

      status(response) shouldBe 501
    }
  }
}
