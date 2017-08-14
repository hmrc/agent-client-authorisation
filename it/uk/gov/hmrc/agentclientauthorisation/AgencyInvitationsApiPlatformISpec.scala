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
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.AgentCode
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
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyResource(arn))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agencyUrl(arn))
    behave like anEndpointThatPreventsAccessToAnotherAgenciesInvitations(agencyUrl(arn))
  }

  "GET /agencies/:arn/invitations" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(agencyInvitationsUrl(arn))
    behave like anEndpointThatPreventsAccessToAnotherAgenciesInvitations(agencyInvitationsUrl(arn))
  }

  "GET /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn))

    s"return 403 NO_PERMISSION_ON_AGENCY for someone else's invitation list" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).isLoggedIn().andIsSubscribedToAgentServices()
      val response = agencyGetSentInvitations(arn)
      response should matchErrorResult(NoPermissionOnAgency)
    }
  }

  "POST /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly{
      agencySendInvitation(arn, validInvitation)
    }
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn))

    "Return 404 for an invitation that doesn't exist" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      val response = agencyGetSentInvitation(arn, BSONObjectID.generate.stringify)
      response should matchErrorResult(InvitationNotFound)
    }

    s"Return 403 NO_PERMISSION_ON_AGENCY if accessing someone else's invitation" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord()
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      val location = agencySendInvitation(arn, validInvitation).header("location")

      given().agentAdmin(Arn("98765"), AgentCode("123456")).isLoggedIn().andIsSubscribedToAgentServices()
      val response = new Resource(location.get, port).get()
      response should matchErrorResult(NoPermissionOnAgency)
    }
  }

  "/agencies/:arn/invitations" should {

    "should not create invitation if postcodes do not match" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(clientPostcode = "BA1 1AA")) should matchErrorResult(PostcodeDoesNotMatch)
    }

    "should not create invitation if postcode is not in a valid format" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(clientPostcode = "BAn 1AA")) should matchErrorResult(postcodeFormatInvalid(
        """The submitted postcode, "BAn 1AA", does not match the expected format."""))
    }

    "should not create invitation for an unsupported service" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord()
      val response = agencySendInvitation(arn, validInvitation.copy(service = "sa"))
      withClue(response.body) {
        response should matchErrorResult(unsupportedService("Unsupported service \"sa\", the only currently supported service is \"HMRC-MTD-IT\""))
      }
    }

    "should not create invitation for an identifier type" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord()
      val response = agencySendInvitation(arn, validInvitation.copy(clientIdType = "MTDITID"))
      withClue(response.body) {
        response should matchErrorResult(unsupportedClientIdType("Unsupported clientIdType \"MTDITID\", the only currently supported type is \"ni\""))
      }
    }

    "should not create invitation for non-UK address" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord(countryCode = "AU")
      agencySendInvitation(arn, validInvitation) should matchErrorResult(nonUkAddress("AU"))
    }

    "should not create invitation for invalid NINO" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(clientId = "NOTNINO")) should matchErrorResult(InvalidNino)
    }

    "should create invitation if postcode has no spaces" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      agencySendInvitation(arn, validInvitation.copy(clientPostcode = "AA11AA")).status shouldBe 201
    }

    "should create invitation if postcode has more than one space" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      val response = agencySendInvitation(arn, validInvitation.copy(clientPostcode = "A A1 1A A"))
      withClue(response.body) {
        response.status shouldBe 201
      }
    }

    "should not create invitation if DES does not return any Business Partner Record" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      given().client(clientId = nino).hasNoBusinessPartnerRecord
      agencySendInvitation(arn, validInvitation.copy()) should matchErrorResult(ClientRegistrationNotFound)
    }
  }

  "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyCancelInvitation(arn, "invitaionId"))

    "should return 204 when invitation is Cancelled" ignore {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()
      val location = agencySendInvitation(arn, validInvitation.copy(clientPostcode = "AA11AA")).header("location")
      val response = agencyGetSentInvitation(arn, location.get)
      response.status shouldBe 204
    }
  }
}

trait AgencyInvitationsISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours with ApiRequests with ErrorResultMatchers {

  protected implicit val arn = Arn("ABCDEF12345678")
  protected val otherAgencyArn: Arn = Arn("98765")
  protected val otherAgencyCode: AgentCode = AgentCode("123456")
  protected implicit val agentCode = AgentCode("LMNOP123456")

  protected val MtdItService = "HMRC-MTD-IT"
  protected val nino = nextNino
  protected val validInvitation: AgencyInvitationRequest = AgencyInvitationRequest(MtdItService, "ni", nino.value, "AA1 1AA")

//  "GET root resource" should {
//    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(baseUrl)
//    behave like anEndpointAccessibleForMtdAgentsOnly(rootResource)
//  }

  def anEndpointWithMeaningfulContentForAnAuthorisedAgent(url:String): Unit = {
    "return a meaningful response for the authenticated agent" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andIsSubscribedToAgentServices()

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "sent" \ "href").as[String] shouldBe externalUrl(agencyGetInvitationsUrl(arn))
    }
  }

  def anEndpointThatPreventsAccessToAnotherAgenciesInvitations(url:String): Unit = {
    "return 403 for someone else's invitations" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).isLoggedIn().andIsSubscribedToAgentServices()
      new Resource(url, port).get() should matchErrorResult(NoPermissionOnAgency)
    }
  }
}
