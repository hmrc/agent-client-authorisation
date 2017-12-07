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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.JsArray
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.StatusUpdateFailure
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ClientEndpointBehaviours, ResettingMockitoSugar, TestData}
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, MtdItId}
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Generator, Nino}

import scala.concurrent.{ExecutionContext, Future}

class ClientInvitationsControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with ClientEndpointBehaviours with TestData {
  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]

  val controller = new ClientInvitationsController(invitationsService)(metrics, microserviceAuthConnector, auditService) {
    override val authConnector: PlayAuthConnector = mockPlayAuthConnector
  }

  val invitationDbId: String = BSONObjectID.generate.stringify
  val invitationId: InvitationId = InvitationId("ABBBBBBBBBBCC")
  val generator = new Generator()
  val nino: Nino = nino1

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any[ExecutionContext])).thenReturn(returnValue)

  "getDetailsForClient" should {
    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStub(clientEnrolments)

      val response = await(controller.getDetailsForClient(MtdItId("invalid"))(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }
  }

  "Accepting an invitation" should {
    "Return no content" in {
      clientAuthStub(clientEnrolments)

      val invitation = anInvitation(nino)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsAccepted thenReturn (Future successful Right(transitionInvitation(invitation, Accepted)))

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))
      response.header.status shouldBe 204
      verifyAgentClientRelationshipCreatedAuditEvent()
    }

    "Return not found when the invitation doesn't exist" in {
      clientAuthStub(clientEnrolments)

      whenFindingAnInvitation thenReturn noInvitation

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))
      response shouldBe InvitationNotFound
      verifyNoAuditEventSent()
    }

    "the invitation cannot be actioned" in {
      clientAuthStub(clientEnrolments)

      whenFindingAnInvitation thenReturn aFutureOptionInvitation()
      whenInvitationIsAccepted thenReturn (Future successful Left(StatusUpdateFailure(Accepted,"failure message")))

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))
      response shouldBe invalidInvitationStatus("failure message")
      verifyNoAuditEventSent()
    }

    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStub(clientEnrolments)

      val response = await(controller.acceptInvitation(MtdItId("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionOnClient
      verifyNoAuditEventSent()
    }

    "Return unauthorised when the user is not logged in to MDTP" in {
      clientAuthStub(failedStubForClient)

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe GenericUnauthorized
      verifyNoAuditEventSent()
    }

    "not change the invitation status if relationship creation fails" in {
      pending
    }
  }

  "Rejecting an invitation" should {
    "Return no content" in {
      clientAuthStub(clientEnrolments)

      val invitation = anInvitation(nino)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsRejected thenReturn (Future successful Right(transitionInvitation(invitation, Rejected)))

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response.header.status shouldBe 204
    }

    "Return not found when the invitation doesn't exist" in {
      clientAuthStub(clientEnrolments)

      whenFindingAnInvitation thenReturn noInvitation

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe InvitationNotFound
    }

    "the invitation cannot be actioned" in {
      clientAuthStub(clientEnrolments)

      whenFindingAnInvitation thenReturn aFutureOptionInvitation()
      whenInvitationIsRejected thenReturn (Future successful Left(StatusUpdateFailure(Rejected, "failure message")))

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe invalidInvitationStatus("failure message")
    }

    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStub(clientEnrolments)

      val response = await(controller.rejectInvitation(MtdItId("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }

    "Return unauthorised when the user is not logged in to MDTP" in {
      clientAuthStub(failedStubForClient)

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe GenericUnauthorized
    }
  }

  "getInvitation" should {
    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStub(clientEnrolments)

      val response = await(controller.getInvitation(MtdItId("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }
  }

  "getInvitations" should {
    "return 200 and an empty list when there are no invitations for the client" in {
      clientAuthStub(clientEnrolments)
      whenClientReceivedInvitation.thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(mtdItId1, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations").get shouldBe JsArray()
    }

    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStub(clientEnrolments)

      val response = await(controller.getInvitations(MtdItId("invalid"), None)(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }

    "not include the invitation ID in invitations to encourage HATEOAS API usage" in {
      clientAuthStub(clientEnrolments)
      whenClientReceivedInvitation.thenReturn(Future successful List(
        Invitation(
          BSONObjectID("abcdefabcdefabcdefabcdef"), invitationId, arn, "MTDITID", mtdItId1.value, "postcode", nino1.value, "ni",
          List(StatusChangeEvent(new DateTime(2016, 11, 1, 11, 30), Accepted)))))

      val result: Result = await(controller.getInvitations(mtdItId1, None)(FakeRequest()))
      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations") (0) \ "id").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations") (0) \ "invitationId").asOpt[String] shouldBe None
    }
  }
}
