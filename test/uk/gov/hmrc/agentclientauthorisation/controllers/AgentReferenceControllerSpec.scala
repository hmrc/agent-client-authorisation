/*
 * Copyright 2021 HM Revenue & Customs
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
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.DesConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentclientauthorisation.service.{AgentLinkService, InvitationsService}
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, ConfidenceLevel, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AgentReferenceControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with TestData {

  val mockAgentReferenceRepository: AgentReferenceRepository = resettingMock[AgentReferenceRepository]
  val mockInvitationsRepository: InvitationsRepository = resettingMock[InvitationsRepository]
  val mockInvitationsService: InvitationsService = resettingMock[InvitationsService]
  val metrics: Metrics = resettingMock[Metrics]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val cc: ControllerComponents = stubControllerComponents()
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)
  val mockDesConnector = resettingMock[DesConnector]
  val appConfig = resettingMock[AppConfig]

  val mockAgentLinkService: AgentLinkService =
    new AgentLinkService(mockAgentReferenceRepository, mockDesConnector, metrics)

  val agentReferenceController =
    new AgentReferenceController(mockAgentLinkService, mockAgentReferenceRepository, mockInvitationsService, appConfig)(
      metrics,
      cc,
      mockPlayAuthConnector,
      global)

  private def clientAuthStub(returnValue: Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments])
    : OngoingStubbing[Future[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments]] =
    when(mockPlayAuthConnector
      .authorise(any(): Predicate, any[Retrieval[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments]]())(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(returnValue)

  "getAgentReferenceRecord" should {

    "return an agent reference record for a given uid" in {
      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      when(mockAgentReferenceRepository.findBy(any(): String)(any(): ExecutionContext))
        .thenReturn(Future.successful(Some(agentReferenceRecord)))

      val result = await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result).as[AgentReferenceRecord] shouldBe agentReferenceRecord
    }

    "return none when the record is not found" in {
      when(mockAgentReferenceRepository.findBy(any(): String)(any(): ExecutionContext))
        .thenReturn(Future.successful(None))

      val result = await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))

      status(result) shouldBe 404
    }

    "return failure when unable to fetch record from mongo" in {
      when(mockAgentReferenceRepository.findBy(any(): String)(any(): ExecutionContext))
        .thenReturn(Future.failed(new Exception("Error")))

      an[Exception] shouldBe thrownBy {
        await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))
      }
    }
  }

  "getAgentReferenceRecord via ARN" should {

    "return an agent reference record for a given arn" in {
      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("mari-anne", "anne-mari"))

      val simplifiedAgentRefRecord: SimplifiedAgentRefRecord =
        SimplifiedAgentRefRecord("ABCDEFGH", arn, "anne-mari")

      when(mockDesConnector.getAgencyDetails(any[Arn])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Some(AgentDetailsDesResponse(
              Option(model.AgencyDetails(Some("anne-mari"), Some("email"), Some(businessAddress))),
              Some(SuspensionDetails(suspensionStatus = false, None))))))

      when(mockAgentReferenceRepository.findByArn(any[Arn])(any[ExecutionContext]))
        .thenReturn(Future.successful(Some(agentReferenceRecord)))

      val result = await(agentReferenceController.getAgentReferenceRecordByArn(arn)(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result).as[SimplifiedAgentRefRecord] shouldBe simplifiedAgentRefRecord
    }
  }

  "getInvitationsInfo" should {
    "return invitationIds and dates" when {
      "authorised for user: Individual" when {
        "confidence level is 200" in testAuthorisedIndividual(ConfidenceLevel.L200)

        "confidence level is greater than 200" in testAuthorisedIndividual(ConfidenceLevel.L300)

        def testAuthorisedIndividual(withThisConfidenceLevel: ConfidenceLevel) = {
          clientAuthStub(client(AffinityGroup.Individual, withThisConfidenceLevel, clientMtdItIrvVat))

          val agentReferenceRecord: AgentReferenceRecord =
            AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

          val expiryDate = LocalDate.now()
          val eventTime = DateTime.now()

          val invitationIdAndExpiryDate1 =
            InvitationInfo(
              InvitationId("ABERULMHCKKW3"),
              expiryDate,
              Pending,
              arn,
              Service.MtdIt,
              false,
              None,
              List(StatusChangeEvent(eventTime, Pending)))
          val invitationIdAndExpiryDate2 =
            InvitationInfo(
              InvitationId("B9SCS2T4NZBAX"),
              expiryDate,
              Pending,
              arn,
              Service.PersonalIncomeRecord,
              false,
              None,
              List(StatusChangeEvent(eventTime, Pending)))
          val invitationIdAndExpiryDate3 =
            InvitationInfo(
              InvitationId("CZTW1KY6RTAAT"),
              expiryDate,
              Pending,
              arn,
              Service.Vat,
              false,
              None,
              List(StatusChangeEvent(eventTime, Pending)))

          val listOfInvitations =
            List(invitationIdAndExpiryDate1, invitationIdAndExpiryDate2, invitationIdAndExpiryDate3)

          when(mockAgentReferenceRepository.findBy(any(): String)(any(): ExecutionContext))
            .thenReturn(Future.successful(Some(agentReferenceRecord)))

          when(
            mockInvitationsService.findInvitationsInfoBy(any[Arn], any[Seq[(String, String, String)]], any[Option[InvitationStatus]])(
              any[ExecutionContext]))
            .thenReturn(Future successful List(invitationIdAndExpiryDate1, invitationIdAndExpiryDate2, invitationIdAndExpiryDate3))

          val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
          status(result) shouldBe 200
          jsonBodyOf(result).as[List[InvitationInfo]] shouldBe listOfInvitations
        }
      }

      "authorised for user: Organisation" in {
        clientAuthStub(clientVatEnrolmentsOrganisation)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        val expiryDate = LocalDate.now()

        val invitationIdAndExpiryDate3 =
          InvitationInfo(
            InvitationId("CZTW1KY6RTAAT"),
            expiryDate,
            Pending,
            arn,
            Service.Vat,
            false,
            None,
            List(StatusChangeEvent(DateTime.now(), Pending)))

        val listOfInvitations = List(invitationIdAndExpiryDate3)

        when(
          mockInvitationsService.findInvitationsInfoBy(any[Arn], any[Seq[(String, String, String)]], any[Option[InvitationStatus]])(
            any[ExecutionContext]))
          .thenReturn(Future successful listOfInvitations)

        when(mockAgentReferenceRepository.findBy(any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationInfo]] shouldBe listOfInvitations
      }
    }

    "return nothing if no invitations found" when {
      "no agent-reference-record is found for authorised user: Individual" in {
        clientAuthStub(clientMtdIrvVatEnrolmentsIndividual)

        when(mockAgentReferenceRepository.findBy(any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 404
      }

      "no agent-reference-record is found for authorised user: Organisation" in {
        clientAuthStub(clientVatEnrolmentsOrganisation)

        when(mockAgentReferenceRepository.findBy(any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(None))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 404
      }

      "invitation-store returned nothing for authorised user: Individual" in {
        clientAuthStub(clientMtdIrvVatEnrolmentsIndividual)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        when(
          mockInvitationsService.findInvitationsInfoBy(any[Arn], any[Seq[(String, String, String)]], any[Option[InvitationStatus]])(
            any[ExecutionContext]))
          .thenReturn(Future successful List.empty)

        when(mockAgentReferenceRepository.findBy(any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationInfo]] shouldBe List.empty
      }

      "invitation-store returned nothing for authorised user: Organisation" in {
        clientAuthStub(clientVatEnrolmentsOrganisation)

        val agentReferenceRecord: AgentReferenceRecord =
          AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

        when(
          mockInvitationsService.findInvitationsInfoBy(any[Arn], any[Seq[(String, String, String)]], any[Option[InvitationStatus]])(
            any[ExecutionContext]))
          .thenReturn(Future successful List.empty)

        when(mockAgentReferenceRepository.findBy(any[String])(any[ExecutionContext]))
          .thenReturn(Future.successful(Some(agentReferenceRecord)))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))

        status(result) shouldBe 200
        jsonBodyOf(result).as[List[InvitationInfo]] shouldBe List.empty
      }
    }

    "return unauthorised" when {
      "user: Agent" in {
        clientAuthStub(agentAffinityConfidenceAndEnrolment)

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
        status(result) shouldBe 403
        result shouldBe GenericForbidden
      }

      "user: Individual with confidence level less than 200" in {
        clientAuthStub(client(AffinityGroup.Individual, ConfidenceLevel.L100, clientMtdItIrvVat))

        val result = await(agentReferenceController.getInvitationsInfo("ABCDEFGH", Some(Pending))(FakeRequest()))
        status(result) shouldBe 403
        result shouldBe GenericForbidden
      }
    }
  }
}
