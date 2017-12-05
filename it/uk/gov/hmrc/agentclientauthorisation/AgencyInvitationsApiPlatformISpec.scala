/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, Inspectors}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.InvitationId
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.test.UnitSpec

//class AgencyInvitationsFrontendISpec extends AgencyInvitationsISpec {
//  override val apiPlatform: Boolean = false
//}

class AgencyInvitationsApiPlatformISpec extends AgencyInvitationsISpec {
  "GET /agencies" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agenciesResource)
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agenciesUrl)
  }

  "GET /agencies/:arn" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyResource(arn1))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agencyUrl(arn1))
    behave like anEndpointThatPreventsAccessToAnotherAgenciesInvitations(agencyUrl(arn1))
  }

  "GET /agencies/:arn/invitations" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn1))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agencyInvitationsUrl(arn1))
    behave like anEndpointThatPreventsAccessToAnotherAgenciesInvitations(agencyInvitationsUrl(arn1))
  }

  "GET /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn1))

    s"return 403 NO_PERMISSION_ON_AGENCY for someone else's invitation list" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).isLoggedInAndIsSubscribed
      val response = agencyGetSentInvitations(arn1)
      response should matchErrorResult(NoPermissionOnAgency)
    }
  }

  "POST /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly{
      agencySendInvitation(arn1, validInvitation)
    }
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn1))

    "Return 404 for an invitation that doesn't exist" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      val response = agencyGetSentInvitation(arn1, "ABBBBBBBBC")
      response should matchErrorResult(InvitationNotFound)
    }

    "Return 403 NO_PERMISSION_ON_AGENCY if accessing someone else's invitation" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord()
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      val location = agencySendInvitation(arn1, validInvitation).header("location")

      given().agentAdmin(Arn("98765"), AgentCode("123456")).isLoggedInAndIsSubscribed
      val response = new Resource(location.get, port).get()
      response should matchErrorResult(NoPermissionOnAgency)
    }
  }

  "/agencies/:arn/invitations" should {

    "should not create invitation if postcodes do not match" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord()
      agencySendInvitation(arn1, validInvitation.copy(clientPostcode = "BA1 1AA")) should matchErrorResult(PostcodeDoesNotMatch)
    }

    "should not create invitation if postcode is not in a valid format" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord()
      agencySendInvitation(arn1, validInvitation.copy(clientPostcode = "BAn 1AA")) should matchErrorResult(postcodeFormatInvalid(
        """The submitted postcode, "BAn 1AA", does not match the expected format."""))
    }

    "should not create invitation for an unsupported service" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord()
      val response = agencySendInvitation(arn1, validInvitation.copy(service = "sa"))
      withClue(response.body) {
        response should matchErrorResult(unsupportedService("Unsupported service \"sa\", the only currently supported service is \"HMRC-MTD-IT\""))
      }
    }

    "should not create invitation for an identifier type" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord()
      val response = agencySendInvitation(arn1, validInvitation.copy(clientIdType = "MTDITID"))
      withClue(response.body) {
        response should matchErrorResult(unsupportedClientIdType("Unsupported clientIdType \"MTDITID\", the only currently supported type is \"ni\""))
      }
    }

    "should not create invitation for non-UK address" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord(countryCode = "AU")
      agencySendInvitation(arn1, validInvitation) should matchErrorResult(nonUkAddress("AU"))
    }

    "should not create invitation for invalid NINO" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecord()
      agencySendInvitation(arn1, validInvitation.copy(clientId = "NOTNINO")) should matchErrorResult(InvalidNino)
    }

    "should create invitation if postcode has no spaces" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      agencySendInvitation(arn1, validInvitation.copy(clientPostcode = "AA11AA")).status shouldBe 201
    }

    "should create invitation if postcode has more than one space" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      val response = agencySendInvitation(arn1, validInvitation.copy(clientPostcode = "A A1 1A A"))
      withClue(response.body) {
        response.status shouldBe 201
      }
    }

    "should not create invitation if DES does not return any Business Partner Record" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasNoBusinessPartnerRecord
      agencySendInvitation(arn1, validInvitation.copy()) should matchErrorResult(ClientRegistrationNotFound)
    }
  }
}

trait AgencyInvitationsISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours with ApiRequests with ErrorResultMatchers {

  protected implicit val arn1 = Arn(arn)
  protected val otherAgencyArn: Arn = Arn("98765")
  protected val otherAgencyCode: AgentCode = AgentCode("123456")
  protected implicit val agentCode1 = AgentCode(agentCode)

  protected val nino: Nino = nextNino
  protected val validInvitation: AgencyInvitationRequest = AgencyInvitationRequest(MtdItService, "ni", nino.value, "AA1 1AA")

//  "GET root resource" should {
//    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(baseUrl)
//    behave like anEndpointAccessibleForMtdAgentsOnly(rootResource)
//  }

  def anEndpointWithMeaningfulContentForAnAuthorisedAgent(url:String): Unit = {
    "return a meaningful response for the authenticated agent" in {
      given().agentAdmin(arn1, agentCode1).isLoggedInAndIsSubscribed

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "sent" \ "href").as[String] shouldBe externalUrl(agencyGetInvitationsUrl(arn1))
    }
  }

  def anEndpointThatPreventsAccessToAnotherAgenciesInvitations(url:String): Unit = {
    "return 403 for someone else's invitations" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).isLoggedInAndIsSubscribed
      new Resource(url, port).get() should matchErrorResult(NoPermissionOnAgency)
    }
  }
}
