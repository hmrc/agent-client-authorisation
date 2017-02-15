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

import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.test.UnitSpec

class ClientInvitationsApiPlatformISpec extends ClientInvitationsISpec

class ClientInvitationsFrontendISpec extends ClientInvitationsISpec {
  override val apiPlatform: Boolean = false
}

trait ClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests with ErrorResultMatchers {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val nino = nextNino
  private val nino1 = nextNino

  private val invitationsUrl = s"${clientUrl(nino)}/invitations"
  private val invitationsReceivedUrl = s"$invitationsUrl/received"

  "GET /" should {
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(baseUrl)
    behave like anEndpointAccessibleForSaClientsOnly(nino)(rootResource())
  }

  "GET /clients" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientsResource())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientsUrl)
  }

  "GET /clients/:clientId" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientResource(nino))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientUrl(nino))
    behave like anEndpointThatPreventsAccessToAnotherClientsInvitations(clientUrl(nino))
  }

  "GET /clients/:clientId/invitations" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(new Resource(invitationsUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(invitationsUrl)
    behave like anEndpointThatPreventsAccessToAnotherClientsInvitations(invitationsUrl)
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientAcceptInvitation(nino, "invitation-id-not-used"))
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientRejectInvitation(nino, "invitation-id-not-used"))
  }

  "GET /clients/:clientId/invitations/received" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientGetReceivedInvitations(nino))

    "return 403 NO_PERMISSION_ON_CLIENT when try to access someone else's invitations" in {

      given().client(clientId = nino).isLoggedIn()
      clientGetReceivedInvitations(nino1) should matchErrorResult(NoPermissionOnClient)
    }
  }

  "GET /clients/:clientId/invitations/received/:invitation" should {
    // an invitationId that is a valid BSONObjectID but for which no invitation exists
    val invitationId: String = "585d24f57a83a131006bb746"
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientGetReceivedInvitation(nino, invitationId))

    "return 404 when invitation not found" in {
      given().client(clientId = nino).isLoggedIn()

      val response = clientGetReceivedInvitation(nino, invitationId)
      response should matchErrorResult(InvitationNotFound)
    }

    "return 404 when invitationId is not a valid BSONObjectID" in {
      given().client(clientId = nino).isLoggedIn()

      val response = clientGetReceivedInvitation(nino, "invite-id-never-used")
      response should matchErrorResult(InvitationNotFound)
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to get someone else's invitations" in {
      val invite = sendInvitationToClient(nino1)

      val client = new ClientApi(this, nino, port)
      given().client(clientId = client.clientId).isLoggedIn()

      val response = getReceivedInvitationResource(invite.links.selfLink)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to transition someone else's invitation" in {
      val invite = sendInvitationToClient(nino1)

      val client = new ClientApi(this, nino, port)
      given().client(clientId = client.clientId).isLoggedIn()

      val response = updateInvitationResource(invite.links.acceptLink.get)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }
  }

  private def sendInvitationToClient(clientId: Nino): EmbeddedInvitation = {
    val agency = new AgencyApi(this, arn, port)
    given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

    agency.sendInvitation(clientId)

    val client = new ClientApi(this, clientId, port)
    given().client(clientId = client.clientId).isLoggedIn()
    val invitations = client.getInvitations()
    invitations.firstInvitation
  }

  def anEndpointWithMeaningfulContentForAnAuthorisedClient(url:String): Unit = {
    "return a meaningful response for the authenticated clients" in {
      given().client(clientId = nino).isLoggedIn().aRelationshipIsCreatedWith(arn)

      val response = new Resource(url, port).get()

      response.status shouldBe 200
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe externalUrl(invitationsReceivedUrl)
    }
  }

  def anEndpointThatPreventsAccessToAnotherClientsInvitations(url:String): Unit = {
    "return 403 NO_PERMISSION_ON_CLIENT for someone else's invitations" in {

      given().client(clientId = nino1).isLoggedIn()

      val response = new Resource(url, port).get

      response should matchErrorResult(NoPermissionOnClient)
    }
  }
}
