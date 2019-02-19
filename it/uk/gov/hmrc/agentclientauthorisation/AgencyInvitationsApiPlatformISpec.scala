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

package uk.gov.hmrc.agentclientauthorisation

import java.time.LocalDate

import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, Inspectors}
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Vrn}
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.test.UnitSpec

class AgencyInvitationsApiPlatformISpec extends AgencyInvitationsISpec {

  "GET /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn1))

    s"return 403 NO_PERMISSION_ON_AGENCY for someone else's invitation list" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).givenAuthorisedAsAgent(otherAgencyArn)
      val response = agencyGetSentInvitations(arn1)
      response should matchErrorResult(NoPermissionOnAgency)
    }
  }

  "POST /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly {
      agencySendInvitation(arn1, validInvitation)
    }
  }

  "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {
    behave like anEndpointAccessibleForMtdAgentsOnly {
      agencyCancelInvitation(arn1, invitationId.value)
    }
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn1))

    "Return 404 for an invitation that doesn't exist" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      val response = agencyGetSentInvitation(arn1, "ABBBBBBBBBBCC")
      response should matchErrorResult(InvitationNotFound)
    }

    "Return 403 NO_PERMISSION_ON_AGENCY if accessing someone else's invitation" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasABusinessPartnerRecord(nino)
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId(nino)

      val location = agencySendInvitation(arn1, validInvitation).header("location")

      given().agentAdmin(otherAgencyArn, AgentCode("123456")).givenAuthorisedAsAgent(otherAgencyArn)
      val response = new Resource(location.get, port).get()
      response should matchErrorResult(NoPermissionOnAgency)
    }
  }

  "/agencies/:arn/invitations" should {

    "should not create invitation for an unsupported service" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasABusinessPartnerRecord(nino)
      val response = agencySendInvitation(arn1, validInvitation.copy(service = "sa"))
      withClue(response.body) {
        response should matchErrorResult(unsupportedService("Unsupported service \"sa\""))
      }
    }

    "should not create invitation for an identifier type" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasABusinessPartnerRecord(nino)
      val response = agencySendInvitation(arn1, validInvitation.copy(clientIdType = "MTDITID"))
      withClue(response.body) {
        response should matchErrorResult(
          unsupportedClientIdType("Unsupported clientIdType \"MTDITID\", for service type \"HMRC-MTD-IT\""))
      }
    }

    "should not create invitation for invalid NINO" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasABusinessPartnerRecord(nino)
      agencySendInvitation(arn1, validInvitation.copy(clientId = "NOTNINO")) should matchErrorResult(InvalidClientId)
    }

    "should create invitation if postcode has no spaces" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId(nino)

      agencySendInvitation(arn1, validInvitation.copy(clientPostcode = Some("AA11AA"))).status shouldBe 201
    }

    "should create invitation if postcode has more than one space" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId(nino)

      val response = agencySendInvitation(arn1, validInvitation.copy(clientPostcode = Some("A A1 1A A")))
      withClue(response.body) {
        response.status shouldBe 201
      }
    }

    "should not create invitation if DES does not return any Business Partner Record" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = nino).hasNoBusinessPartnerRecord(nino)
      agencySendInvitation(arn1, validInvitationWithPostcode) should matchErrorResult(ClientRegistrationNotFound)
    }
  }

  "/known-facts/individuals/nino/:nino/sa/postcode/:postcode" should {
    val clientNino = Nino("ZR987654C")
    val postcode = "AA11AA"

    behave like anEndpointAccessibleForMtdAgentsOnly(agentGetCheckItsaKnownFact(clientNino, postcode))

    "return 204 when ITSA information in ETMP has matching postcode" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientNino).hasABusinessPartnerRecordWithMtdItId(clientNino)
      agentGetCheckItsaKnownFact(clientNino, postcode).status shouldBe 204
    }

    "return 400 when given invalid postcode" in {
      val expectedError = Json.parse(s"""|{
                                         |"code":"POSTCODE_FORMAT_INVALID",
                                         |"message":"The submitted postcode, AA1!AA, does not match the expected format."
                                         |}""".stripMargin)
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientNino).hasABusinessPartnerRecordWithMtdItId(nino)
      val result = agentGetCheckItsaKnownFact(clientNino, "AA1 !AA")
      Json.parse(result.body) shouldBe expectedError
      result.status shouldBe 400
    }

    "return 403 when customer ITSA information in ETMP but has a non-matching postcode" in {
      val expectedError = Json.parse(s"""|{
                                         |"code":"POSTCODE_DOES_NOT_MATCH",
                                         |"message":"The submitted postcode did not match the client's postcode as held by HMRC."
                                         |}""".stripMargin)

      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientNino).hasABusinessPartnerRecordWithMtdItId(clientNino)
      val result = agentGetCheckItsaKnownFact(clientNino, "DH14EJ")
      Json.parse(result.body) shouldBe expectedError
      result.status shouldBe 403
    }

    "return 400 when postcode is not present when attempting to search" in {
      val expectedError = Json.parse(s"""|{
                                         |"code":"POSTCODE_REQUIRED",
                                         |"message":"Postcode is required for service HMRC-MTD-IT"
                                         |}""".stripMargin)
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientNino).hasABusinessPartnerRecordWithMtdItId(nino)
      val result = agentGetCheckItsaKnownFact(clientNino, "%20")
      Json.parse(result.body) shouldBe expectedError
      result.status shouldBe 400
    }

    "return 501 when postcode is not a UK Address" in {
      val expectedError = Json.parse(s"""|
                                         |{"code":"NON_UK_ADDRESS",
                                         |"message":"This API does not currently support non-UK addresses. The client's country code should be 'GB' but it was 'PL'."
                                         |}""".stripMargin)
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientNino).hasABusinessPartnerRecord(clientNino, countryCode = "PL")
      val result = agentGetCheckItsaKnownFact(clientNino, "AA11AA")
      Json.parse(result.body) shouldBe expectedError
      result.status shouldBe 501
    }
  }

  "/known-facts/organisations/vat/:vrn/registration-date/:vatRegistrationDate" should {
    val clientVrn = Vrn("101747641")
    val effectiveRegistrationDate = LocalDate.parse("2017-04-01")

    behave like anEndpointAccessibleForMtdAgentsOnly(agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate))

    "return 204 when customer VAT information in ETMP has a matching effectiveRegistrationDate" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientVrn).hasVatCustomerDetails(clientVrn, effectiveRegistrationDate.toString, isEffectiveRegistrationDatePresent = true)
      agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate).status shouldBe 204
    }

    "return 403 when customer VAT information in ETMP but has a non-matching effectiveRegistrationDate" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientVrn).hasVatCustomerDetails(clientVrn, effectiveRegistrationDate.toString, isEffectiveRegistrationDatePresent = true)
      val expectedError = Json.parse(s"""|{
                                         |"code":"VAT_REGISTRATION_DATE_DOES_NOT_MATCH",
                                         |"message":"The submitted VAT registration date did not match the client's VAT registration date as held by HMRC."
                                         |}""".stripMargin)

      val result = agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate.plusDays(1))
      result.status shouldBe 403
      Json.parse(result.body) shouldBe expectedError
    }

    "return 404 when customer VAT information in ETMP but effectiveRegistrationDate is not present" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientVrn).hasVatCustomerDetails(clientVrn, effectiveRegistrationDate.toString, isEffectiveRegistrationDatePresent = false)
      agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate).status shouldBe 404
    }

    "return 404 when ETMP returns json without any 'approvedInformation' present" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientVrn).hasVatCustomerDetailsWithNoApprovedInformation(clientVrn)
      agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate).status shouldBe 404
    }

    "return 404 when no customer VAT information is in ETMP" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientVrn).hasNoVatCustomerDetails(clientVrn)
      agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate).status shouldBe 404
    }

    "return 502 when DES/ETMP is unavailable" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      given().client(clientId = clientVrn).failsVatCustomerDetails(clientVrn, 503)
      agentGetCheckVatKnownFact(clientVrn, effectiveRegistrationDate).status shouldBe 502
    }
  }

  "/known-facts/individuals/:nino/dob/:dob" should {
    val clientNino = Nino("ZR987654C")
    val dateOfBirth = LocalDate.parse("1988-01-01")

    behave like anEndpointAccessibleForMtdAgentsOnly(agentGetCheckIrvKnownFact(clientNino, dateOfBirth))

    "return 204 when customer IRV information in Citizen Details has a matching dateOfBirth" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      givenCitizenDetailsAreKnownFor(clientNino.value, "01011988")
      agentGetCheckIrvKnownFact(clientNino, dateOfBirth).status shouldBe 204
    }

    "return 403 when customer IRV information in Citizen Details but has a non-matching dateOfBirth" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      givenCitizenDetailsAreKnownFor(clientNino.value, "01011988")
      val expectedError = Json.parse(s"""|
                                         |{"code":"DATE_OF_BIRTH_DOES_NOT_MATCH",
                                         |"message":"The submitted date of birth did not match the client's date of birth as held by HMRC."
                                         |}""".stripMargin)

      val result = agentGetCheckIrvKnownFact(clientNino, dateOfBirth.plusDays(1))
      result.status shouldBe 403
      Json.parse(result.body) shouldBe expectedError
    }

    "return 403 when customer IRV information in Citizen Details but dateOfBirth is not present" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      givenCitizenDetailsNoDob(clientNino.value)
      agentGetCheckIrvKnownFact(clientNino, dateOfBirth).status shouldBe 403
    }

    "return 404 when no customer IRV information is in Citizen Details" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      givenCitizenDetailsReturnsResponseFor(clientNino.value, 404)
      agentGetCheckIrvKnownFact(clientNino, dateOfBirth).status shouldBe 404
    }

    "return 404 when Nino is invalid" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      givenCitizenDetailsReturnsResponseFor(clientNino.value, 400)
      agentGetCheckIrvKnownFact(clientNino, dateOfBirth).status shouldBe 404
    }

    "return 404 when Citizen Details returns more than one matching result" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)
      givenCitizenDetailsReturnsResponseFor(clientNino.value, 500)
      agentGetCheckIrvKnownFact(clientNino, dateOfBirth).status shouldBe 404
    }
  }
}

trait AgencyInvitationsISpec
    extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours
    with ApiRequests with ErrorResultMatchers with CitizenDetailsStub {

  protected implicit val arn1 = Arn(arn)
  protected val otherAgencyArn: Arn = Arn("98765")
  protected val otherAgencyCode: AgentCode = AgentCode("123456")
  protected implicit val agentCode1 = AgentCode(agentCode)

  protected val nino: Nino = nextNino
  protected val validInvitation: AgencyInvitationRequest = AgencyInvitationRequest(MtdItService, "ni", nino.value, None)
  protected val validInvitationWithPostcode: AgencyInvitationRequest =
    AgencyInvitationRequest(MtdItService, "ni", nino.value, Some("AA1 1AA"))
  protected val invitationId: InvitationId =
    InvitationId.create(arn1.value, mtdItId1.value, "HMRC-MTD-IT", DateTime.parse("2001-01-01"))('A')

  //  "GET root resource" should {
  //    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(baseUrl)
  //    behave like anEndpointAccessibleForMtdAgentsOnly(rootResource)
  //  }

  def anEndpointWithMeaningfulContentForAnAuthorisedAgent(url: String): Unit =
    "return a meaningful response for the authenticated agent" in {
      given().agentAdmin(arn1, agentCode1).givenAuthorisedAsAgent(arn1)

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "sent" \ "href").as[String] shouldBe externalUrl(agencyGetInvitationsUrl(arn1))
    }

  def anEndpointThatPreventsAccessToAnotherAgenciesInvitations(url: String): Unit =
    "return 403 for someone else's invitations" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).givenAuthorisedAsAgent(arn1)
      new Resource(url, port).get() should matchErrorResult(NoPermissionOnAgency)
    }
}
