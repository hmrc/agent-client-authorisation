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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import org.bson.types.ObjectId
import play.api.libs.json.{JsResultException, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, Invitation, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.model.{AuthorisationRequest, Invitation}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Service.{CapitalGains, MtdIt, MtdItSupp, PersonalIncomeRecord, Pillar2, Ppt, Trust, Vat}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, InvitationId, Service}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipsConnectorISpec extends UnitSpec with AppAndStubs with ACRStubs with TestDataSupport with RelationshipStubs {

  val connector: RelationshipsConnector = app.injector.instanceOf[RelationshipsConnector]

  def invitation(clientType: Option[String] = personal, service: Service, clientId: TaxIdentifier, suppliedClientId: TaxIdentifier) = Invitation(
    invitationId = InvitationId("123"),
    arn = arn,
    clientType = clientType,
    service = service,
    clientId = clientId,
    suppliedClientId = suppliedClientId,
    expiryDate = LocalDate.now(),
    detailsForEmail = None,
    clientActionUrl = None,
    events = List.empty
  )

  "getActiveRelationships" should {

    "return the map of active relationships when multiple present" in {
      stubFor(
        get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""{
                           |  "HMRC-MTD-IT": ["AARN1187295", "AARN1187296"],
                           |  "HMRC-MTD-VAT": ["AARN1187259"],
                           |  "HMRC-TERS-ORG": ["AARN1187259"],
                           |  "HMRC-PPT-ORG": ["AARN1187259"]
                           |}""".stripMargin)
          )
      )

      val result = await(connector.getActiveRelationships)
      result.get("HMRC-MTD-IT") should contain.only(Arn("AARN1187295"), Arn("AARN1187296"))
      result.get("HMRC-MTD-VAT") should contain.only(Arn("AARN1187259"))
      result.get("HMRC-TERS-ORG") should contain.only(Arn("AARN1187259"))
      result.get("HMRC-PPT-ORG") should contain.only(Arn("AARN1187259"))
    }

    "return the map of active relationships when only one present" in {
      stubFor(
        get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""{
                           |  "HMRC-MTD-IT": ["AARN1187295"]
                           |}""".stripMargin)
          )
      )

      val result = await(connector.getActiveRelationships).get

      result("HMRC-MTD-IT") should contain.only(Arn("AARN1187295"))
      result.get("HMRC-MTD-VAT") shouldBe None
      result.get("HMRC-TERS-ORG") shouldBe None
      result.get("HMRC-PPT-ORG") shouldBe None
    }

    "return an empty map of active relationships when none available" in {
      stubFor(
        get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""{}""".stripMargin)
          )
      )

      val result = await(connector.getActiveRelationships).get

      result.get("HMRC-MTD-IT") shouldBe None
      result.get("HMRC-MTD-VAT") shouldBe None
      result.get("HMRC-TERS-ORG") shouldBe None
      result.get("HMRC-PPT-ORG") shouldBe None
    }
  }

  "getActiveAfiRelationships" should {

    "return the list of active relationships" in {
      stubFor(
        get(urlEqualTo(s"/agent-fi-relationship/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""[
                           |  {
                           |    "service": "service123",
                           |    "clientId": "clientId123",
                           |    "relationshipStatus": "Active"
                           |  }
                           |]""".stripMargin)
          )
      )

      val result = await(connector.getActiveAfiRelationships)
      result should not be empty
    }
  }

  "createMtdItRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, serviceITSA, "MTDITID", mtdItId)
      val result = await(connector.createMtdItRelationship(invitation(Some("personal"), MtdIt, mtdItId, mtdItId)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, serviceITSA, "MTDITID", mtdItId)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createMtdItRelationship(invitation(Some("personal"), MtdIt, mtdItId, mtdItId)))
      }
    }
  }

  "createMtdItSuppRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, serviceITSASupp, "MTDITID", mtdItId)
      val result = await(connector.createMtdItSuppRelationship(invitation(Some("personal"), MtdItSupp, mtdItId, mtdItId)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, serviceITSASupp, "MTDITID", mtdItId)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createMtdItSuppRelationship(invitation(Some("personal"), MtdItSupp, mtdItId, mtdItId)))
      }
    }
  }

  "createMtdVATRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, serviceVAT, "VRN", vrn)
      val result = await(connector.createMtdVatRelationship(invitation(Some("personal"), Vat, vrn, vrn)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, serviceVAT, "VRN", vrn)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createMtdVatRelationship(invitation(Some("personal"), Vat, vrn, vrn)))
      }
    }
  }

  "createAfiRelationship" should {
    "return () if the response is 2xx" in {
      anAfiRelationshipIsCreatedWith(arn, nino)
      val result = await(connector.createAfiRelationship(invitation(Some("personal"), PersonalIncomeRecord, nino, nino), LocalDateTime.now()))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      anAfiRelationshipIsCreatedFails(arn, nino)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createAfiRelationship(invitation(Some("personal"), PersonalIncomeRecord, nino, nino), LocalDateTime.now()))
      }
    }
  }

  "createTrustRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, serviceTERS, "SAUTR", utr)
      val result = await(connector.createTrustRelationship(invitation(Some("personal"), Trust, utr, utr)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, serviceTERS, "SAUTR", utr)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createTrustRelationship(invitation(Some("personal"), Trust, utr, utr)))
      }
    }
  }

  "createCapitalGainsTaxRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, serviceCGT, "CGTPDRef", cgtRef)
      val result = await(connector.createCapitalGainsRelationship(invitation(Some("personal"), CapitalGains, cgtRef, cgtRef)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, serviceCGT, "CGTPDRef", cgtRef)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createCapitalGainsRelationship(invitation(Some("personal"), CapitalGains, cgtRef, cgtRef)))
      }
    }
  }

  "createPlasticPackagingTaxRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, servicePPT, "EtmpRegistrationNumber", pptRef)
      val result = await(connector.createPlasticPackagingTaxRelationship(invitation(Some("personal"), Ppt, pptRef, pptRef)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, servicePPT, "EtmpRegistrationNumber", pptRef)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createPlasticPackagingTaxRelationship(invitation(Some("personal"), Ppt, pptRef, pptRef)))
      }
    }
  }

  "createPillar2TaxRelationship" should {
    "return () if the response is 2xx" in {
      givenCreateRelationship(arn, servicePillar2, "PLRID", plrId)
      val result = await(connector.createPillar2Relationship(invitation(Some("personal"), Pillar2, plrId, plrId)))
      result shouldBe (())
    }

    "Throw an exception if the response is 5xx" in {
      givenCreateRelationshipFails(arn, servicePillar2, "PLRID", plrId)

      assertThrows[UpstreamErrorResponse] {
        await(connector.createPillar2Relationship(invitation(Some("personal"), Pillar2, plrId, plrId)))
      }
    }
  }

  "replaceUrnWithUtr" should {

    "return true if the downstream response is 204" in {
      stubUrnToUtrCall("XXTRUST12345678", NO_CONTENT)
      val result = await(connector.replaceUrnWithUtr("XXTRUST12345678", "0123456789"))
      result shouldBe true
    }

    "return false if the downstream response is 404" in {
      stubUrnToUtrCall("XXTRUST12345678", NOT_FOUND)
      val result = await(connector.replaceUrnWithUtr("XXTRUST12345678", "0123456789"))
      result shouldBe false
    }

    "throw an exception if the downstream response is not expected" in {
      stubUrnToUtrCall("XXTRUST12345678", INTERNAL_SERVER_ERROR)
      assertThrows[UpstreamErrorResponse] {
        await(connector.replaceUrnWithUtr("XXTRUST12345678", "0123456789"))
      }
    }
  }

  "sendAuthorisationRequest" when {
    val authorisationRequest = AuthorisationRequest("clientId", "clientIdType", "clientName", "service", Some("clientType"))

    "given a valid request" should {
      "return the invitationId" in {
        stubCreateInvitationCall("ARN123", CREATED, """{"invitationId": "42"}""")

        val result = await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        result shouldBe "42"
      }
    }

    "receiving an error response for an unmatched error case" should {
      "return the error reason" in {
        stubCreateInvitationCall("ARN123", BAD_REQUEST, "")

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for invalid JSON" should {
      "return the error reason" in {
        stubCreateInvitationCall("ARN123", BAD_REQUEST, "Invalid payload: ERROR[INVALID JSON]")

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for unsupported service" should {
      "return the error reason" in {
        val jsonResponse = s"""{"code": "UNSUPPORTED_SERVICE", "message": "Unsupported service ${authorisationRequest.service}"}"""
        stubCreateInvitationCall("ARN123", NOT_IMPLEMENTED, jsonResponse)

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for invalid client id" should {
      "return the error reason" in {
        val jsonResponse =
          s"""{"code": "INVALID_CLIENT_ID", "message": "Invalid clientId ${authorisationRequest.clientId}, for service type ${authorisationRequest.service}"}"""
        stubCreateInvitationCall("ARN123", BAD_REQUEST, jsonResponse)

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for unsupported client id type" should {
      "return the error reason" in {
        val jsonResponse =
          s"""{"code": "UNSUPPORTED_CLIENT_ID_TYPE", "message": "Unsupported clientIdType ${authorisationRequest.suppliedClientIdType}, for service type ${authorisationRequest.service}"}"""
        stubCreateInvitationCall("ARN123", BAD_REQUEST, jsonResponse)

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for unsupported client type" should {
      "return the error reason" in {
        val jsonResponse =
          s"""{"code": "UNSUPPORTED_CLIENT_TYPE", "message": "Unsupported clientType ${authorisationRequest.clientType}"}"""
        stubCreateInvitationCall("ARN123", BAD_REQUEST, jsonResponse)

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for not being able to find the user's MTDfB/alt-itsa registration" should {
      "return the error reason" in {
        val msg =
          s"""The Client's MTDfB registration or SAUTR (if alt-itsa is enabled) was not found. for clientId ${authorisationRequest.clientId}, for clientIdType ${authorisationRequest.suppliedClientIdType}, for service type ${authorisationRequest.service}"""
        val jsonResponse =
          s"""{"code": "CLIENT_REGISTRATION_NOT_FOUND", "message": "$msg"}"""
        stubCreateInvitationCall("ARN123", FORBIDDEN, jsonResponse)

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for a duplicate invitation" should {
      "return the error reason" in {
        val msg =
          s"""An authorisation request for this service has already been created and is awaiting the client’s response. for clientId ${authorisationRequest.clientId}, for clientIdType ${authorisationRequest.suppliedClientIdType}, for service type ${authorisationRequest.service}"""
        val jsonResponse =
          s"""{"code": "DUPLICATE_AUTHORISATION_REQUEST", "message": "$msg"}"""
        stubCreateInvitationCall("ARN123", FORBIDDEN, jsonResponse)

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for internal server error" should {
      "return the error reason" in {
        stubCreateInvitationCall("ARN123", INTERNAL_SERVER_ERROR, "")

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

    "receiving an error response for bad gateway" should {
      "return the error reason" in {
        stubCreateInvitationCall("ARN123", BAD_GATEWAY, "")

        assertThrows[UpstreamErrorResponse] {
          await(connector.sendAuthorisationRequest("ARN123", authorisationRequest))
        }
      }
    }

  }

  "lookupInvitations" should {

    val acrJson = Json.obj(
      "invitationId"         -> "123",
      "arn"                  -> "XARN1234567",
      "service"              -> "HMRC-MTD-VAT",
      "clientId"             -> "123456789",
      "clientIdType"         -> "vrn",
      "suppliedClientId"     -> "234567890",
      "suppliedClientIdType" -> "vrn",
      "clientName"           -> "Macrosoft",
      "status"               -> "Accepted",
      "relationshipEndedBy"  -> "Me",
      "clientType"           -> "personal",
      "expiryDate"           -> "2020-01-01",
      "created"              -> "2020-02-02T00:00:00Z",
      "lastUpdated"          -> "2020-03-03T00:00:00Z"
    )

    val vatInvitation = Invitation(
      invitationId = InvitationId("123"),
      arn = Arn("XARN1234567"),
      clientType = Some("personal"),
      service = Vat,
      clientId = ClientIdentifier("123456789", "vrn"),
      suppliedClientId = ClientIdentifier("234567890", "vrn"),
      expiryDate = LocalDate.parse("2020-01-01"),
      detailsForEmail = None,
      isRelationshipEnded = true,
      relationshipEndedBy = Some("Me"),
      events = List(
        StatusChangeEvent(LocalDateTime.parse("2020-02-02T00:00:00"), Pending),
        StatusChangeEvent(LocalDateTime.parse("2020-03-03T00:00:00"), Accepted)
      ),
      clientActionUrl = None,
      fromAcr = true
    )

    val staticObjectId = ObjectId.get()

    "build the correct query params, parse the response and return invitations" when {

      "using the 'arn' query param" in {
        val expectedQueryParams = "?arn=XARN1234567"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(Some(Arn("XARN1234567")), Seq(), Seq(), None))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }

      "using one 'services' query param" in {
        val expectedQueryParams = "?services=HMRC-MTD-VAT"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(None, Seq(Vat), Seq(), None))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }

      "using multiple 'services' query params" in {
        val expectedQueryParams = "?services=HMRC-MTD-VAT&services=HMRC-MTD-IT"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(None, Seq(Vat, MtdIt), Seq(), None))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }

      "using one 'clientIds' query param" in {
        val expectedQueryParams = "?clientIds=123456789"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(None, Seq(), Seq("123456789"), None))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }

      "using multiple 'clientIds' query params" in {
        val expectedQueryParams = "?clientIds=123456789&clientIds=234567890"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(None, Seq(), Seq("123456789", "234567890"), None))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }

      "using the 'status' query param" in {
        val expectedQueryParams = "?status=Accepted"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(None, Seq(), Seq(), Some(Accepted)))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }

      "using all query params" in {
        val expectedQueryParams = "?arn=XARN1234567&services=HMRC-MTD-VAT&clientIds=123456789&status=Accepted"
        stubLookupInvitations(expectedQueryParams, OK, Json.arr(acrJson))
        val result = await(connector.lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), Some(Accepted)))

        result.head.copy(_id = staticObjectId) shouldBe vatInvitation.copy(_id = staticObjectId)
      }
    }

    "return an empty sequence if the response is 404" in {
      val expectedQueryParams = "?arn=XARN1234567&services=HMRC-MTD-VAT&clientIds=123456789&status=Accepted"
      stubLookupInvitations(expectedQueryParams, NOT_FOUND)
      val result = await(connector.lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), Some(Accepted)))
      result shouldBe Seq()
    }

    "throw an exception if the JSON data is in an invalid format" in {
      val expectedQueryParams = "?arn=XARN1234567&services=HMRC-MTD-VAT&clientIds=123456789&status=Accepted"
      stubLookupInvitations(expectedQueryParams, OK, Json.obj("ff" -> "gg"))
      assertThrows[JsResultException] {
        await(connector.lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), Some(Accepted)))
      }
    }

    "throw an exception if the downstream response is not expected" in {
      val expectedQueryParams = "?arn=XARN1234567&services=HMRC-MTD-VAT&clientIds=123456789&status=Accepted"
      stubLookupInvitations(expectedQueryParams, INTERNAL_SERVER_ERROR)
      assertThrows[UpstreamErrorResponse] {
        await(connector.lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), Some(Accepted)))
      }
    }
  }
}
