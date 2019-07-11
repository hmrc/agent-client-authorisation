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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import play.api.libs.json.{OFormat, Reads}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ClientEndpointBehaviours, ResettingMockitoSugar, TestData}
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.auth.core.{Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{utr, utr2}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TrustClientControllerSpec
    extends AkkaMaterializerSpec with ResettingMockitoSugar with ClientEndpointBehaviours with TestData {

  val metrics: Metrics = mock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = mock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockHalResource: ClientInvitationsHal = mock[ClientInvitationsHal]
  val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }

  val controller =
    new TrustClientInvitationsController(invitationsService)(
      metrics,
      microserviceAuthConnector,
      auditService,
      ecp,
      "maintain agent relationships",
      "maintain_agent_relationships") {
      override val authConnector: PlayAuthConnector = mockPlayAuthConnector
    }

  val invitationDbId: String = BSONObjectID.generate.stringify
  val invitationId: InvitationId = InvitationId("DT6NLAYD6FSYU")
  val generator = new Generator()

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  private def clientAuthStubForStride(returnValue: Future[~[Enrolments, Credentials]]) =
    when(
      mockPlayAuthConnector
        .authorise(any(), any[Retrieval[~[Enrolments, Credentials]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  "Accepting Trust Invitation" should {
    val clientTrustCorrect: Future[~[Enrolments, Credentials]] = {
      val retrievals = new ~(Enrolments(clientTrustEnrolment), Credentials("providerId", "GovernmentGateway"))
      Future.successful(retrievals)
    }
    "return 204" in {
      clientAuthStubForStride(clientTrustCorrect)
      val invitation = aTrustInvitation(utr)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsAccepted thenReturn (Future successful Right(transitionInvitation(invitation, Accepted)))

      val response = await(controller.acceptInvitation(utr, invitationId)(FakeRequest()))
      response.header.status shouldBe 204
      verifyAgentClientRelationshipCreatedAuditEvent("HMRC-TERS-ORG")
    }
  }

  "Rejecting Trust Invitation" should {
    val clientTrustCorrect: Future[~[Enrolments, Credentials]] = {
      val retrievals = new ~(Enrolments(clientTrustEnrolment), Credentials("providerId", "GovernmentGateway"))
      Future.successful(retrievals)
    }
    "return 204" in {
      clientAuthStubForStride(clientTrustCorrect)
      val invitation = aTrustInvitation(utr)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsRejected thenReturn (Future successful Right(transitionInvitation(invitation, Rejected)))

      val response = await(controller.rejectInvitation(utr, invitationId)(FakeRequest()))
      response.header.status shouldBe 204
    }
  }

  "Get All Invitations by Utr" should {
    val clientTrustCorrect: Future[~[Enrolments, Credentials]] = {
      val retrievals = new ~(Enrolments(clientTrustEnrolment), Credentials("providerId", "GovernmentGateway"))
      Future.successful(retrievals)
    }

    "return 200" in {

      clientAuthStubForStride(clientTrustCorrect)
      val invitation = aTrustInvitation(utr)
      val invitation2 = aTrustInvitation(utr2)
      whenClientReceivedInvitation thenReturn (Future successful Seq(invitation, invitation2))

      val response = await(controller.getInvitations(utr, None)(FakeRequest()))
      response.header.status shouldBe 200
      ((jsonBodyOf(response) \ "_embedded" \ "invitations")(0) \ "clientId").as[String] shouldBe invitation.clientId
        .value
      ((jsonBodyOf(response) \ "_embedded" \ "invitations")(1) \ "clientId")
        .as[String] shouldBe invitation2.clientId.value
    }
  }

}
