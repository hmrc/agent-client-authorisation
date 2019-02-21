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

import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, Service}
import uk.gov.hmrc.agentclientauthorisation.model.Service.{MtdIt, PersonalIncomeRecord}
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.{AgentCode, Nino, TaxIdentifier}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

class ClientInvitationsApiPlatformISpec extends ClientInvitationsISpec {
  //  "GET /clients" should {
  //    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientsResource())
  //    behave like anEndpointWithMeaningfulContentForAnAuthorisedClient(clientsUrl)
  //  }

  "PUT of /clients/MTDITID/:mtdItId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientAcceptInvitation(mtdItId1, "ABBBBBBBBBBCC"))
  }

  "PUT of /clients/MTDITID/:mtdItId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientRejectInvitation(mtdItId1, "ABBBBBBBBBBCC"))
  }

  "GET /clients/MTDITID/:mtdItId/invitations/received" should {
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientGetReceivedInvitations(mtdItId1))

    "return 403 NO_PERMISSION_ON_CLIENT when try to access someone else's invitations" in {

      given().client(clientId = nino, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1)
      clientGetReceivedInvitations(MtdItId("0123456789")) should matchErrorResult(NoPermissionOnClient)
    }
  }

  "GET /clients/MTDITID/:mtdItId/invitations/received/:invitation" should {
    val invitationId: String = "ABBBBBBBBBBCC"
    behave like anEndpointAccessibleForSaClientsOnly(nino)(clientGetReceivedInvitation(mtdItId1, invitationId))

    "return 404 when invitation not found" in {
      given().client(clientId = nino, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1)

      val response = clientGetReceivedInvitation(mtdItId1, invitationId)
      response should matchErrorResult(InvitationNotFound)
    }

    "return 404 when invitationId is not valid" in {
      given().client(clientId = nino, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1)

      val response = clientGetReceivedInvitation(mtdItId1, "INVALIDINV")
      response should matchErrorResult(InvitationNotFound)
    }

    "return 200 when trying to get their own invitation" in {
      val myNino = nino
      val invite = sendInvitationToClient(myNino)

      val client = new ClientApi(this, myNino, mtdItId1, port)
      given().client(clientId = client.suppliedClientId, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1).givenGetAgentName(arn1)

      val response = getReceivedInvitationResource(invite.links.selfLink)(port, client.hc)
      response.status shouldBe 200
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to get someone else's invitations for MTD" in {
      val invite = sendInvitationToClient(nino)

      val client = new ClientApi(this, nino, mtdItId1, port)
      given().client(clientId = client.suppliedClientId).givenClientMtdItId(MtdItId("otherMtdItId"))

      val response = getReceivedInvitationResource(invite.links.selfLink)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to get someone else's invitations for PIR" in {
      val invite = sendInvitationToClient(nino, service = PersonalIncomeRecord)

      val clientNino = nino1
      val client = new ClientApi(this, clientNino, clientNino, port)
      given().client(clientId = client.suppliedClientId).givenClientNi(clientNino)

      val response = getReceivedInvitationResource(invite.links.selfLink)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }

    "return 403 NO_PERMISSION_ON_CLIENT when trying to transition someone else's invitation" in {
      val invite = sendInvitationToClient(nino)

      val client = new ClientApi(this, nino1, MtdItId("0123456789"), port)
      given()
        .client(clientId = client.suppliedClientId, canonicalClientId = MtdItId("0123456789"))
        .givenClientMtdItId(MtdItId("otherMtdItId"))

      val response = updateInvitationResource(invite.links.acceptLink.get)(port, client.hc)
      response should matchErrorResult(NoPermissionOnClient)
    }
  }
}

class ClientInvitationsFrontendISpec extends ClientInvitationsISpec {
  override val apiPlatform: Boolean = false
}

trait ClientInvitationsISpec
    extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside with ApiRequests
    with ErrorResultMatchers {

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

  protected def sendInvitationToClient(clientId: Nino, service: Service = MtdIt): EmbeddedInvitation = {
    val canonicalClientId: TaxIdentifier = service match {
      case MtdIt                => mtdItId1
      case PersonalIncomeRecord => clientId
    }

    val agency = new AgencyApi(this, arn1, port)
    given().agentAdmin(arn1).givenAuthorisedAsAgent(arn1)
    given()
      .client(clientId = clientId, canonicalClientId = canonicalClientId)
      .hasABusinessPartnerRecordWithMtdItId(clientId, mtdItId1)

    agency.sendInvitation(clientId, service = service.id)

    val clientApi = new ClientApi(this, clientId, canonicalClientId, port)
    val client = given().client(clientId = clientApi.suppliedClientId, canonicalClientId = canonicalClientId)

    service match {
      case MtdIt                => client.givenClientMtdItId(mtdItId1)
      case PersonalIncomeRecord => client.givenClientNi(clientId)
    }

    val invitations = clientApi.getInvitations()
    invitations.firstInvitation
  }

  def anEndpointWithMeaningfulContentForAnAuthorisedClient(url: String): Unit =
    "return a meaningful response for the authenticated clients" in {
      given().client(clientId = nino, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1)
      //        .aRelationshipIsCreatedWith(arn1)

      val response = new Resource(url, port).get()

      withClue(response.body) {
        response.status shouldBe 200
      }
      (response.json \ "_links" \ "self" \ "href").as[String] shouldBe externalUrl(url)
      (response.json \ "_links" \ "received" \ "href").as[String] shouldBe externalUrl(invitationsReceivedUrl)
    }

  def anEndpointThatPreventsAccessToAnotherClientsInvitations(url: String): Unit =
    "return 403 NO_PERMISSION_ON_CLIENT for someone else's invitations" in {

      given().client(clientId = nino1).givenClientMtdItId(mtdItId1)

      val response = new Resource(url, port).get

      response should matchErrorResult(NoPermissionOnClient)
    }
}
