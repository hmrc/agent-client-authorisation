/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.test.UnitSpec

class AgencyInvitationsISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours with APIRequests {

  private implicit val arn = Arn("ABCDEF12345678")
  private val otherAgencyArn: Arn = Arn("98765")
  private val otherAgencyCode: AgentCode = AgentCode("123456") 
  private implicit val agentCode = AgentCode("LMNOP123456")

  private val MtdRegime: Regime = Regime("mtd-sa")
  private val validInvitation: AgencyInvitationRequest = AgencyInvitationRequest(MtdRegime, MtdClientId("1234567899"), "AA1 1AA")

  "GET root resource" should {
    behave like anEndpointWithMeaningfulContentForAnAuthorisedAgent(baseUrl)
    behave like anEndpointAccessibleForMtdAgentsOnly(rootResource)
  }
  
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

    "return 403 for someone else's invitation list" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = agencyGetSentInvitations(arn)
      response.status shouldBe 403
    }
  }

  "POST /agencies/:arn/invitations" should {
    behave like anEndpointAccessibleForMtdAgentsOnly{
      agencySendInvitation(arn, validInvitation)
    }
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(agencyGetSentInvitations(arn))

    "Return 404 for an invitation that doesn't exist" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = agencyGetSentInvitation(arn, BSONObjectID.generate.stringify)
      response.status shouldBe 404
    }

    "Return 403 if accessing someone else's invitation" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val location = agencySendInvitation(arn, validInvitation).header("location")

      given().agentAdmin(Arn("98765"), AgentCode("123456")).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = new Resource(location.get, port).get()
      response.status shouldBe 403
    }
  }

  "/agencies/:arn/invitations" should {

    "should not create invitation if postcodes do not match" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(postcode = "BA1 1AA")).status shouldBe 403
    }

    "should not create invitation if postcode is not in a valid format" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(postcode = "BAn 1AA")).status shouldBe 400
    }

    "should not create invitation for an unsupported regime" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = agencySendInvitation(arn, validInvitation.copy(regime = Regime("sa")))
      response.status shouldBe 501
      (response.json \ "code").as[String] shouldBe "UNSUPPORTED_REGIME"
      (response.json \ "message").as[String] shouldBe "Unsupported regime \"sa\", the only currently supported regime is \"mtd-sa\""
    }

    "should create invitation if postcode has no spaces" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(postcode = "AA11AA")).status shouldBe 201
    }

    "should create invitation if postcode has more than one space" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      agencySendInvitation(arn, validInvitation.copy(postcode = "A A1 1A A")).status shouldBe 201
    }

    "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {
      behave like anEndpointAccessibleForMtdAgentsOnly(agencyCancelInvitation(arn, "invitaionId"))

      "should return 204 when invitation is Cancelled" ignore {
        given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
        val location = agencySendInvitation(arn, validInvitation.copy(postcode = "AA11AA")).header("location")
        val response = agencyGetSentInvitation(arn, location.get)
        response.status shouldBe 204
      }
    }
  }

   def anEndpointWithMeaningfulContentForAnAuthorisedAgent(url:String): Unit = {
    "return a meaningful response for the authenticated agent" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "sent" \ "href").as[String] shouldBe externalUrl(agencyGetInvitationsUrl(arn))
    }
  }

  def anEndpointThatPreventsAccessToAnotherAgenciesInvitations(url:String): Unit = {
    "return 403 for someone else's invitations" in {
      given().agentAdmin(otherAgencyArn, otherAgencyCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      new Resource(url, port).get().status shouldBe 403
    }
  }
}
