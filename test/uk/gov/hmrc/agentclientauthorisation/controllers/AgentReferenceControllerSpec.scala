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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import org.joda.time.LocalDate
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentclientauthorisation.model.{InvitationIdAndExpiryDate, Pending}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentclientauthorisation.service.AgentLinkService
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._

import scala.concurrent.{ExecutionContext, Future}

class AgentReferenceControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with TestData {

  val mockAgentReferenceRepository: AgentReferenceRepository = resettingMock[AgentReferenceRepository]
  val mockInvitationsRepository: InvitationsRepository = resettingMock[InvitationsRepository]
  val metrics: Metrics = resettingMock[Metrics]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val mockAgentServicesAccountConnector: AgentServicesAccountConnector = resettingMock[AgentServicesAccountConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)

  val mockAgentLinkService: AgentLinkService =
    new AgentLinkService(mockAgentReferenceRepository, mockAgentServicesAccountConnector, auditService, metrics)

  val agentReferenceController =
    new AgentReferenceController(mockAgentReferenceRepository, mockInvitationsRepository)(
      metrics,
      mockPlayAuthConnector,
      auditService)

  private def clientAuthStub(returnValue: Future[~[Option[AffinityGroup], ~[ConfidenceLevel, Enrolments]]])
    : OngoingStubbing[Future[Option[AffinityGroup] ~ (ConfidenceLevel ~ Enrolments)]] =
    when(
      mockPlayAuthConnector.authorise(
        any(),
        any[Retrieval[~[Option[AffinityGroup], ~[ConfidenceLevel, Enrolments]]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  "getAgentReferenceRecord" should {

    "return an agent reference record for a given uid" in {
      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      when(mockAgentReferenceRepository.findBy(any())(any()))
        .thenReturn(Future.successful(Some(agentReferenceRecord)))

      val result = await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result).as[AgentReferenceRecord] shouldBe agentReferenceRecord
    }

    "return none when the record is not found" in {
      when(mockAgentReferenceRepository.findBy(any())(any()))
        .thenReturn(Future.successful(None))

      val result = await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))

      status(result) shouldBe 404
    }

    "return failure when unable to fetch record from mongo" in {
      when(mockAgentReferenceRepository.findBy(any())(any()))
        .thenReturn(Future.failed(new Exception("Error")))

      an[Exception] shouldBe thrownBy {
        await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))
      }
    }
  }

  "getInvitationsInfo" should {
    "return invitationIds and dates" when {
      "authorised for user: Individual" in {
        clientAuthStub(clientMtdIrvVatEnrolmentsIndividual)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        val expiryDate = LocalDate.now()

        val invitationIdAndExpiryDate1 = InvitationIdAndExpiryDate(InvitationId("ABERULMHCKKW3"), expiryDate)
        val invitationIdAndExpiryDate2 = InvitationIdAndExpiryDate(InvitationId("B9SCS2T4NZBAX"), expiryDate)
        val invitationIdAndExpiryDate3 = InvitationIdAndExpiryDate(InvitationId("CZTW1KY6RTAAT"), expiryDate)

        val listOfInvitations = List(invitationIdAndExpiryDate1, invitationIdAndExpiryDate2, invitationIdAndExpiryDate3)

        when(mockAgentReferenceRepository.findBy(any())(any()))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        when(mockInvitationsRepository.findAllInvitationIdAndExpiryDate(any[Arn], any(), any())(any()))
          .thenReturn(
            Future successful List(invitationIdAndExpiryDate1, invitationIdAndExpiryDate2, invitationIdAndExpiryDate3))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationIdAndExpiryDate]] shouldBe listOfInvitations
      }

      "authorised for user: Organisation" in {
        clientAuthStub(clientVatEnrolmentsOrganisation)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        val expiryDate = LocalDate.now()

        val invitationIdAndExpiryDate3 = InvitationIdAndExpiryDate(InvitationId("CZTW1KY6RTAAT"), expiryDate)

        val listOfInvitations = List(invitationIdAndExpiryDate3)

        when(mockInvitationsRepository.findAllInvitationIdAndExpiryDate(any[Arn], any(), any())(any()))
          .thenReturn(Future successful listOfInvitations)

        when(mockAgentReferenceRepository.findBy(any())(any()))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationIdAndExpiryDate]] shouldBe listOfInvitations
      }
    }

    "return nothing if no invitations found" when {
      "no agent-reference-record is found for authorised user: Individual" in {
        clientAuthStub(clientMtdIrvVatEnrolmentsIndividual)

        when(mockAgentReferenceRepository.findBy(any())(any()))
          .thenReturn(Future.successful(None))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 404
      }

      "no agent-reference-record is found for authorised user: Organisation" in {
        clientAuthStub(clientVatEnrolmentsOrganisation)

        when(mockAgentReferenceRepository.findBy(any())(any()))
          .thenReturn(Future.successful(None))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 404
      }

      "invitation-store returned nothing for authorised user: Individual" in {
        clientAuthStub(clientMtdIrvVatEnrolmentsIndividual)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        when(mockInvitationsRepository.findAllInvitationIdAndExpiryDate(any[Arn], any(), any())(any()))
          .thenReturn(Future successful List.empty)

        when(mockAgentReferenceRepository.findBy(any())(any()))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationIdAndExpiryDate]] shouldBe List.empty
      }

      "invitation-store returned nothing for authorised user: Organisation" in {
        clientAuthStub(clientVatEnrolmentsOrganisation)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        when(mockInvitationsRepository.findAllInvitationIdAndExpiryDate(any[Arn], any(), any())(any()))
          .thenReturn(Future successful List.empty)

        when(mockAgentReferenceRepository.findBy(any())(any()))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationIdAndExpiryDate]] shouldBe List.empty
      }
    }

    "return unauthorised for user: Agent" in {
      clientAuthStub(agentAffinityConfidenceAndEnrolment)

      val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
      status(result) shouldBe 401
      result shouldBe GenericUnauthorized
    }
  }
}
