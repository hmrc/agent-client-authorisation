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
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Service.{CapitalGains, MtdIt, MtdItSupp, PersonalIncomeRecord, Pillar2, Ppt, Trust, Vat}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Service}
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
}
