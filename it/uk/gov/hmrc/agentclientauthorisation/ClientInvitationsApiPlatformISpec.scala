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
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

class ClientInvitationsApiPlatformISpec extends ClientInvitationsISpec {
  //  "GET /clients" should {
  //    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientsResource())
  //    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientsUrl)
  //  }

  "GET /clients/MTDITID/:mtdItId" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientResource(mtdItId1))
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientUrl(mtdItId1))
    behave like anEndpointThatPreventsAccessToAnotherClientsInvitations(clientUrl(MtdItId("0123456789")))
  }

  "GET /clients/MTDITID/:mtdItId/invitations" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(new Resource(invitationsUrl, port).get())
    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(invitationsUrl)
    behave like anEndpointThatPreventsAccessToAnotherClientsInvitations(invitationsUrl)
  }

  "PUT of /clients/MTDITID/:mtdItId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientAcceptInvitation(mtdItId1, "invitation-id-not-used"))
  }

  "PUT of /clients/MTDITID/:mtdItId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientRejectInvitation(mtdItId1, "invitation-id-not-used"))
  }

  "GET /clients/MTDITID/:mtdItId/invitations/received" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientGetReceivedInvitations(mtdItId1))

        "return 403 NO_PERMISSION_ON_CLIENT when try to access someone else's invitations" in {

          given().client(clientId = nino, canonicalClientId = mtdItId1).isLoggedIn
          clientGetReceivedInvitations(MtdItId("0123456789")) should matchErrorResult(NoPermissionOnClient)
        }
  }

  "GET /clients/MTDITID/:mtdItId/invitations/received/:invitation" should {
    // an invitationId that is a valid BSONObjectID but for which no invitation exists
    val invitationId: String = "585d24f57a83a131006bb746"
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientGetReceivedInvitation(mtdItId1, invitationId))

    "return 404 when invitation not found" in {
      given().client(clientId = nino, canonicalClientId = mtdItId1).isLoggedIn

      val response = clientGetReceivedInvitation(mtdItId1, invitationId)
      response should matchErrorResult(InvitationNotFound)
    }

    "return 404 when invitationId is not a valid BSONObjectID" in {
      given().client(clientId = nino, canonicalClientId = mtdItId1).isLoggedIn

      val response = clientGetReceivedInvitation(mtdItId1, "invite-id-never-used")
      response should matchErrorResult(InvitationNotFound)
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to get someone else's invitations" in {
      val invite = sendInvitationToClient(nino)

      val client = new ClientApi(this, nino1, MtdItId("0123456789"), port)
      given().client(clientId = client.clientId, canonicalClientId = MtdItId("0123456789")).isLoggedIn

      val response = getReceivedInvitationResource(invite.links.selfLink)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to transition someone else's invitation" in {
      val invite = sendInvitationToClient(nino)

      val client = new ClientApi(this, nino1, MtdItId("0123456789"), port)
      given().client(clientId = client.clientId, canonicalClientId = MtdItId("0123456789")).isLoggedIn

      val response = updateInvitationResource(invite.links.acceptLink.get)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }
  }
}


class ClientInvitationsFrontendISpec extends ClientInvitationsISpec {
  override val apiPlatform: Boolean = false
}

trait ClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests with ErrorResultMatchers {

  protected implicit val arn1 = Arn(arn)
  protected implicit val agentCode1 = AgentCode(agentCode)
  protected val nino: Nino = nextNino
  protected val nino1: Nino = nextNino

  protected val invitationsUrl = s"${clientUrl(mtdItId1)}/invitations"
  protected val invitationsReceivedUrl = s"$invitationsUrl/received"

  //  "GET /" should {
  //    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(baseUrl)
  //    behave like anEndpointAccessibleForSaClientsOnly(nino)(rootResource())
  //  }

  protected def sendInvitationToClient(clientId: Nino): EmbeddedInvitation = {
    val agency = new AgencyApi(this, arn1, port)
    given().agentAdmin(arn1).isLoggedInAndIsSubscribed
    given().client(clientId = clientId, canonicalClientId = mtdItId1).hasABusinessPartnerRecordWithMtdItId(mtdItId1)

    agency.sendInvitation(clientId)

    val client = new ClientApi(this, clientId, mtdItId1, port)
    given().client(clientId = client.clientId, canonicalClientId = mtdItId1).isLoggedIn
    val invitations = client.getInvitations()
    invitations.firstInvitation
  }

  def anEndpointWithMeaningfulContentForAnAuthorisedClient(url: String): Unit = {
    "return a meaningful response for the authenticated clients" in {
      given().client(clientId = nino, canonicalClientId = mtdItId1).isLoggedIn.aRelationshipIsCreatedWith(arn1)

      val response = new Resource(url, port).get()

      withClue(response.body) {
        response.status shouldBe 200
      }
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe externalUrl(invitationsReceivedUrl)
    }
  }

  def anEndpointThatPreventsAccessToAnotherClientsInvitations(url: String): Unit = {
    "return 403 NO_PERMISSION_ON_CLIENT for someone else's invitations" in {

      given().client(clientId = nino1).isLoggedIn

      val response = new Resource(url, port).get

      response should matchErrorResult(NoPermissionOnClient)
    }
  }
}
