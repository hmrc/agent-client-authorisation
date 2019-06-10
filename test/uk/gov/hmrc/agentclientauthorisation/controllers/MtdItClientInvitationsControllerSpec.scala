/*
 * Copyright 2019 HM Revenue & Customs
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
import javax.inject.Provider
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.i18n.{Messages, MessagesApi}
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.service.ClientNameService
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.auth.core.retrieve.~
import uk.gov.hmrc.http.HeaderCarrier
//import play.api.libs.functional._
import play.api.libs.json.JsArray
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.StatusUpdateFailure
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, MtdItId}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval}
import uk.gov.hmrc.auth.core.{Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Generator, Nino}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class MtdItClientInvitationsControllerSpec
    extends AkkaMaterializerSpec with ResettingMockitoSugar with ClientEndpointBehaviours with TestData {

  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val mockEmailConnector: EmailConnector = resettingMock[EmailConnector]
  val mockAsaConnector: AgentServicesAccountConnector = resettingMock[AgentServicesAccountConnector]
  val mockClientNameService: ClientNameService = resettingMock[ClientNameService]
  val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }
  implicit val hc: HeaderCarrier = resettingMock[HeaderCarrier]

  val controller =
    new MtdItClientInvitationsController(invitationsService)(
      metrics,
      microserviceAuthConnector,
      mockEmailConnector,
      mockAsaConnector,
      mockClientNameService,
      auditService,
      ecp,
      "maintain agent relationships",
      "maintain_agent_relationships"
    ) {
      override val authConnector: PlayAuthConnector = mockPlayAuthConnector
    }

  val invitationDbId: String = BSONObjectID.generate.stringify
  val invitationId: InvitationId = InvitationId("ABBBBBBBBBBCC")
  val generator = new Generator()
  val nino: Nino = nino1

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  private def clientAuthStubForStride(returnValue: Future[~[Enrolments, Credentials]]) =
    when(
      mockPlayAuthConnector
        .authorise(any(), any[Retrieval[~[Enrolments, Credentials]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  private def agencyEmailStub = when(mockAsaConnector.getAgencyEmailBy(arn)).thenReturn(Future("abc@xyz.com"))

  "Accepting an invitation" should {
    val clientMtdItCorrect: Future[~[Enrolments, Credentials]] = {
      val retrievals = new ~(Enrolments(clientMtdItEnrolment), Credentials("providerId", "GovernmentGateway"))
      Future.successful(retrievals)
    }
    "Return no content" in {
      clientAuthStubForStride(clientMtdItCorrect)
      agencyEmailStub

      val invitation = anInvitation(nino)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsAccepted thenReturn (Future successful Right(transitionInvitation(invitation, Accepted)))

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))
      response.header.status shouldBe 204
      verifyAgentClientRelationshipCreatedAuditEvent()
    }

    "Return not found when the invitation doesn't exist" in {
      clientAuthStubForStride(clientMtdItCorrect)

      whenFindingAnInvitation thenReturn noInvitation

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))
      response shouldBe InvitationNotFound
      verifyNoAuditEventSent()
    }

    "the invitation cannot be actioned" in {
      clientAuthStubForStride(clientMtdItCorrect)

      whenFindingAnInvitation thenReturn aFutureOptionInvitation()
      whenInvitationIsAccepted thenReturn (Future successful Left(StatusUpdateFailure(Accepted, "failure message")))

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))
      response shouldBe invalidInvitationStatus("failure message")
      verifyNoAuditEventSent()
    }

    "Return NoPermissionToPerformOperation when given mtdItId does not match authMtdItId" in {
      clientAuthStubForStride(clientMtdItCorrect)

      val response = await(controller.acceptInvitation(MtdItId("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionToPerformOperation
      verifyNoAuditEventSent()
    }

    "Return unauthorised when the user is not logged in to MDTP" in {
      clientAuthStub(failedStubForClient)

      val response = await(controller.acceptInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe GenericUnauthorized
      verifyNoAuditEventSent()
    }

    "not change the invitation status if relationship creation fails" in
      pending
  }

  "Rejecting an invitation" should {
    val clientMtdItCorrect: Future[~[Enrolments, Credentials]] = {
      val retrievals = new ~(Enrolments(clientMtdItEnrolment), Credentials("providerId", "GovernmentGateway"))
      Future.successful(retrievals)
    }
    "Return no content" in {
      clientAuthStubForStride(clientMtdItCorrect)

      val invitation = anInvitation(nino)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsRejected thenReturn (Future successful Right(transitionInvitation(invitation, Rejected)))

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response.header.status shouldBe 204
    }

    "Return not found when the invitation doesn't exist" in {
      clientAuthStubForStride(clientMtdItCorrect)

      whenFindingAnInvitation thenReturn noInvitation

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe InvitationNotFound
    }

    "the invitation cannot be actioned" in {
      clientAuthStubForStride(clientMtdItCorrect)

      whenFindingAnInvitation thenReturn aFutureOptionInvitation()
      whenInvitationIsRejected thenReturn (Future successful Left(StatusUpdateFailure(Rejected, "failure message")))

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe invalidInvitationStatus("failure message")
    }

    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStubForStride(clientMtdItCorrect)

      val response = await(controller.rejectInvitation(MtdItId("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionToPerformOperation
    }

    "Return unauthorised when the user is not logged in to MDTP" in {
      clientAuthStub(failedStubForClient)

      val response = await(controller.rejectInvitation(mtdItId1, invitationId)(FakeRequest()))

      response shouldBe GenericUnauthorized
    }
  }

  "getInvitation" should {
    "Return NoPermissionOnClient when given mtdItId does not match authMtdItId" in {
      clientAuthStub(clientMtdItEnrolments)

      val response = await(controller.getInvitation(MtdItId("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }
  }

  "getInvitationsClient" should {
    val clientMtdItCorrect: Future[~[Enrolments, Credentials]] = {
      val retrievals = new ~(Enrolments(clientMtdItEnrolment), Credentials("providerId", "GovernmentGateway"))
      Future.successful(retrievals)
    }
    "return 200 and an empty list when there are no invitations for the client" in {
      clientAuthStubForStride(clientMtdItCorrect)
      whenClientReceivedInvitation.thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(mtdItId1, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations").get shouldBe JsArray()
    }

    //TODO Delete this Test / Change Later
    "return 200 and an empty list when there are no invitations for the client when stride user" in {
      val strideEnrolment: Set[Enrolment] =
        Set(Enrolment("maintain agent relationships", Seq.empty, state = "Activated", delegatedAuthRule = None))

      val strideUser: Future[Enrolments ~ Credentials] = {
        val retrievals =
          new ~(Enrolments(strideEnrolment), Credentials("providerId", "PrivilegedApplication"))
        Future.successful(retrievals)
      }
      clientAuthStubForStride(strideUser)
      whenClientReceivedInvitation.thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(mtdItId1, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations").get shouldBe JsArray()
    }

    "return 200 and an empty list when there are no invitations for the client when stride user (alternative format)" in {
      val strideEnrolment: Set[Enrolment] =
        Set(Enrolment("maintain_agent_relationships", Seq.empty, state = "Activated", delegatedAuthRule = None))

      val strideUser: Future[Enrolments ~ Credentials] = {
        val retrievals =
          new ~(Enrolments(strideEnrolment), Credentials("providerId", "PrivilegedApplication"))
        Future.successful(retrievals)
      }
      clientAuthStubForStride(strideUser)
      whenClientReceivedInvitation.thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(mtdItId1, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations").get shouldBe JsArray()
    }

    "Return NoPermissionToPerformOperation when given mtdItId does not match authMtdItId" in {
      clientAuthStubForStride(clientMtdItCorrect)

      val response = await(controller.getInvitations(MtdItId("invalid"), None)(FakeRequest()))

      response shouldBe NoPermissionToPerformOperation
    }

    "include the invitation ID in invitations" in {
      clientAuthStubForStride(clientMtdItCorrect)
      whenClientReceivedInvitation.thenReturn(
        Future successful List(
          TestConstants.defaultInvitation.copy(
            invitationId = invitationId,
            arn = arn,
            clientId = mtdItId1,
            suppliedClientId = nino1,
            events = List(StatusChangeEvent(new DateTime(2016, 11, 1, 11, 30), Accepted)))))

      val result: Result = await(controller.getInvitations(mtdItId1, None)(FakeRequest()))
      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "id").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "invitationId").asOpt[String] shouldBe Some(
        invitationId.value)
    }
  }
}
