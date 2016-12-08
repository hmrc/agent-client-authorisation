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

import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.test.UnitSpec

class ClientInvitationsApiPlatformISpec extends ClientInvitationsISpec

class ClientInvitationsFrontendISpec extends ClientInvitationsISpec {
  override val apiPlatform: Boolean = false
}

trait ClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val mtdClientId = FakeMtdClientId.random()
  private val mtdClient2Id = FakeMtdClientId.random()

  private val invitationsUrl = s"${clientUrl(mtdClientId)}/invitations"
  private val invitationsReceivedUrl = s"$invitationsUrl/received"

  "GET /" should {
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(baseUrl)
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(rootResource())
  }

  "GET /clients" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientsResource())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientsUrl)
  }
 
  "GET /clients/:clientId" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientResource(mtdClientId))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientUrl(mtdClientId))
    behave like anEndpointThatPreventsAccessToAnotherClientsInvitations(clientUrl(mtdClientId))
  }

  "GET /clients/:clientId/invitations" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(new Resource(invitationsUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(invitationsUrl)
    behave like anEndpointThatPreventsAccessToAnotherClientsInvitations(invitationsUrl)
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientAcceptInvitation(mtdClientId, "invitation-id-not-used"))
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientRejectInvitation(mtdClientId, "invitation-id-not-used"))
  }

  "GET /clients/:clientId/invitations/received" should {
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientGetReceivedInvitations(mtdClientId))

    "return 403 when try to access someone else's invitations" in {

      given().client(clientId = mtdClientId).isLoggedIn()
      clientGetReceivedInvitations(mtdClient2Id).status shouldBe 403
    }
  }

  "GET /clients/:clientId/invitations/received/:invitation" should {
    val invitationId: String = "invite-id-never-used"
    behave like anEndpointAccessibleForSaClientsOnly(mtdClientId)(clientGetReceivedInvitation(mtdClientId, invitationId))

    "return 404 when invitation not found" in {
      val response = clientGetReceivedInvitation(mtdClientId, invitationId)
      response.status shouldBe 404
    }

    "return 403 when try to access someone else's invitation" in {

      val agency = new AgencyApi(this, arn, port)
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      agency.sendInvitation(mtdClient2Id)

      val client = new ClientApi(this, mtdClient2Id, port)
      given().client(clientId = client.clientId).isLoggedIn()
      val invitations = client.getInvitations()
      val invite = invitations.firstInvitation

      val client2 = new ClientApi(this, mtdClientId, port)
      given().client(clientId = client2.clientId).isLoggedIn()

      val response = updateInvitationResource(invite.links.acceptLink.get)(port, client2.hc)
      response.status shouldBe 403
    }
  }
  def anEndpointWithMeaningfulContentForAnAuthorisedClient(url:String): Unit = {
    "return a meaningful response for the authenticated agent" in {
      given().client(clientId = mtdClientId).isLoggedIn().aRelationshipIsCreatedWith(arn)

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe externalUrl(invitationsReceivedUrl)
    }
  }

  def anEndpointThatPreventsAccessToAnotherClientsInvitations(url:String): Unit = {
    "return 403 for someone else's invitations" in {

      given().client(clientId = mtdClient2Id).isLoggedIn()

      val response = new Resource(url, port).get

      response.status shouldBe 403
    }
  }
}
