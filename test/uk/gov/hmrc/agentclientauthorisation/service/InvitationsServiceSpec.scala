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

package uk.gov.hmrc.agentclientauthorisation.service

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.joda.time.DateTime.now
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{any, eq => eqs}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.libs.json.JsObject
import play.api.mvc.Request
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID.generate
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val invitationsRepository: InvitationsRepository = mock[InvitationsRepository]
  val agentLinkService: AgentLinkService = mock[AgentLinkService]
  val relationshipsConnector: RelationshipsConnector = mock[RelationshipsConnector]
  val auditService: AuditService = mock[AuditService]
  val desConnector: DesConnector = mock[DesConnector]
  val mockEmailService: EmailService = mock[EmailService]
  val metrics: Metrics = new Metrics {
    override def defaultRegistry: MetricRegistry = new MetricRegistry()
    override def toJson: String = ""
  }

  val service = new InvitationsService(
    invitationsRepository,
    agentLinkService,
    relationshipsConnector,
    desConnector,
    auditService,
    mockEmailService,
    "14 days",
    metrics)

  val ninoAsString: String = nino1.value

  implicit val hc = HeaderCarrier()
  implicit val request: Request[Any] = FakeRequest()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(invitationsRepository, relationshipsConnector)
  }

  "acceptInvitation" should {
    "create a MTDIT relationship" when {
      "invitation status update succeeds" in {
        val invitation = testInvitation
        whenRelationshipIsCreated(invitation) thenReturn (Future successful {})
        val acceptedTestInvitation = transitionInvitation(invitation, Accepted)
        whenStatusIsChangedTo(Accepted) thenReturn (Future successful acceptedTestInvitation)
        when(mockEmailService.sendAcceptedEmail(any())(any(), any())) thenReturn Future(())
        when(mockEmailService.updateEmailDetails(any())(any(), any())) thenReturn Future(invitation)
        when(mockEmailService.sendEmail(any(), any())(any(), any())) thenReturn Future(())
        when(invitationsRepository.removeEmailDetails(any())(any())) thenReturn Future(())

        val response = await(service.acceptInvitation(invitation))

        response shouldBe Right(acceptedTestInvitation)
        verify(invitationsRepository, times(1)).update(any[Invitation], any[InvitationStatus], any[DateTime])(any())
      }

    }

    "change invitation status to Accept" when {
      "relationship already exists" in {
        val invitation = testInvitation
        whenRelationshipIsCreated(invitation) thenReturn (Future failed new Exception("RELATIONSHIP_ALREADY_EXISTS"))
        val acceptedTestInvitation = transitionInvitation(invitation, Accepted)
        whenStatusIsChangedTo(Accepted) thenReturn (Future successful acceptedTestInvitation)
        when(mockEmailService.sendAcceptedEmail(invitation)) thenReturn Future(())
        when(mockEmailService.sendAcceptedEmail(any())(any(), any())) thenReturn Future(())
        when(mockEmailService.updateEmailDetails(any())(any(), any())) thenReturn Future(invitation)
        when(mockEmailService.sendEmail(any(), any())(any(), any())) thenReturn Future(())
        when(invitationsRepository.removeEmailDetails(any())(any())) thenReturn Future(())

        val response = await(service.acceptInvitation(invitation))

        response shouldBe Right(acceptedTestInvitation)
        verify(invitationsRepository, times(1)).update(any[Invitation], any[InvitationStatus], any[DateTime])(any())
      }
    }

    "create a PersonalIncomeRecord relationship" when {
      "invitation status update succeeds" in {
        val invitation = Invitation(
          generate,
          InvitationId.create(arn, mtdItId1.value, Service.PersonalIncomeRecord.id, DateTime.parse("2001-01-01"))('B'),
          Arn(arn),
          Some("personal"),
          Service.PersonalIncomeRecord,
          ClientIdentifier(nino1),
          ClientIdentifier(nino1),
          now().toLocalDate.plusDays(14),
          None,
          None,
          List(StatusChangeEvent(now(), Pending))
        )

        whenAfiRelationshipIsCreated(invitation) thenReturn (Future successful {})
        val acceptedTestInvitation = transitionInvitation(invitation, Accepted)
        whenStatusIsChangedTo(Accepted) thenReturn (Future successful acceptedTestInvitation)
        when(mockEmailService.sendAcceptedEmail(invitation)) thenReturn Future(())

        val response = await(service.acceptInvitation(invitation))

        response shouldBe Right(acceptedTestInvitation)

        verify(invitationsRepository, times(1)).update(any[Invitation], any[InvitationStatus], any[DateTime])(any())
      }

    }

    "should not create a relationship" when {
      "invitation has already been accepted" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Accepted)))

        response shouldBe Left(
          StatusUpdateFailure(
            Accepted,
            "The invitation cannot be transitioned to Accepted because its current status is Accepted. Only Pending invitations may be transitioned to Accepted."
          ))
      }
      "invitation has been cancelled" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Cancelled)))

        response shouldBe Left(
          StatusUpdateFailure(
            Cancelled,
            "The invitation cannot be transitioned to Accepted because its current status is Cancelled. Only Pending invitations may be transitioned to Accepted."
          ))
      }
      "invitation has been rejected" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Rejected)))

        response shouldBe Left(
          StatusUpdateFailure(
            Rejected,
            "The invitation cannot be transitioned to Accepted because its current status is Rejected. Only Pending invitations may be transitioned to Accepted."
          ))
      }
      "invitation has expired" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Expired)))

        response shouldBe Left(
          StatusUpdateFailure(
            Expired,
            "The invitation cannot be transitioned to Accepted because its current status is Expired. Only Pending invitations may be transitioned to Accepted."
          ))
      }
    }

    "should not change the invitation status when relationship creation fails" in {
      val invitation = testInvitation
      whenRelationshipIsCreated(invitation) thenReturn (Future failed ReactiveMongoException("Mongo error"))

      intercept[ReactiveMongoException] {
        await(service.acceptInvitation(invitation))
      }

      verify(invitationsRepository, never()).update(any[Invitation], any[InvitationStatus], any[DateTime])(any())
    }
  }

  "rejectInvitation" should {
    "update the invitation status" in {
      val rejectedTestInvitation = transitionInvitation(testInvitation, Rejected)
      whenStatusIsChangedTo(Rejected) thenReturn rejectedTestInvitation
      when(mockEmailService.sendRejectedEmail(any[Invitation])(any(), any())) thenReturn Future(())

      val response = await(service.rejectInvitation(testInvitation))

      response shouldBe Right(rejectedTestInvitation)
    }

    "not reject a cancelled invitation" in {
      when(mockEmailService.sendRejectedEmail(any[Invitation])(any(), any())) thenReturn Future(())

      val response = await(service.rejectInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe Left(
        StatusUpdateFailure(
          Cancelled,
          "The invitation cannot be transitioned to Rejected because its current status is Cancelled. Only Pending invitations may be transitioned to Rejected."
        ))
    }

    "not reject an accepted invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe Left(
        StatusUpdateFailure(
          Accepted,
          "The invitation cannot be transitioned to Rejected because its current status is Accepted. Only Pending invitations may be transitioned to Rejected."
        ))
    }

    "not reject an already rejected invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe Left(
        StatusUpdateFailure(
          Rejected,
          "The invitation cannot be transitioned to Rejected because its current status is Rejected. Only Pending invitations may be transitioned to Rejected."
        ))
    }
    "not reject an invitation that has expired" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Expired)))

      response shouldBe Left(
        StatusUpdateFailure(
          Expired,
          "The invitation cannot be transitioned to Rejected because its current status is Expired. Only Pending invitations may be transitioned to Rejected."
        ))
    }
  }

  "cancelInvitation" should {
    "update the invitation status" in {
      val cancelledTestInvitation = transitionInvitation(testInvitation, Cancelled)
      whenStatusIsChangedTo(Cancelled) thenReturn cancelledTestInvitation

      val response = await(service.cancelInvitation(testInvitation))

      response shouldBe Right(cancelledTestInvitation)
    }

    "not cancel a cancelled invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe Left(
        StatusUpdateFailure(
          Cancelled,
          "The invitation cannot be transitioned to Cancelled because its current status is Cancelled. Only Pending invitations may be transitioned to Cancelled."
        ))
    }

    "not cancel an accepted invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe Left(
        StatusUpdateFailure(
          Accepted,
          "The invitation cannot be transitioned to Cancelled because its current status is Accepted. Only Pending invitations may be transitioned to Cancelled."
        ))
    }

    "not cancel an already rejected invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe Left(
        StatusUpdateFailure(
          Rejected,
          "The invitation cannot be transitioned to Cancelled because its current status is Rejected. Only Pending invitations may be transitioned to Cancelled."
        ))
    }

    "not cancel an invitation that has expired" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Expired)))

      response shouldBe Left(
        StatusUpdateFailure(
          Expired,
          "The invitation cannot be transitioned to Cancelled because its current status is Expired. Only Pending invitations may be transitioned to Cancelled."
        ))
    }
  }

  "findInvitation" should {
    "return None when the passed invitationId is not in the repository" in {
      when(invitationsRepository.findByInvitationId((any[InvitationId]))(any())).thenReturn(Future.successful(None))
      await(service.findInvitation(InvitationId("INVALIDINV"))) shouldBe None
    }

    "return Some(invitation) when invitation is present" in {
      val invitation = testInvitation
      when(invitationsRepository.findByInvitationId(any[InvitationId])(any()))
        .thenReturn(Future successful Some(invitation))
      when(agentLinkService.getAgentLink(any(), any())(any(), any())).thenReturn(Future.successful("/invitations"))

      await(service.findInvitation(invitation.invitationId)) shouldBe Some(invitation)
    }

    "return some invitation with status Expired when the invitation had a status of expired in the first place" in {
      val invitation = testInvitationWithStatus(Expired)
      when(invitationsRepository.findByInvitationId((any[InvitationId]))(any()))
        .thenReturn(Future successful Some(invitation))

      await(service.findInvitation(invitation.invitationId)).get.status shouldBe Expired
    }
  }

  "translateToMtdItId" should {
    "return the mtfItId if supplied" in {
      val shouldBeMtdItId = await(service.translateToMtdItId(mtdItId1.value, MtdItIdType.id))
      shouldBeMtdItId.head.value shouldBe mtdItId1.value
    }

    "return None if an invalid client id type is supplied" in {
      val shouldBeNone = await(service.translateToMtdItId("id", "noSuchType"))
      shouldBeNone.isEmpty shouldBe true
    }

    "return an mtdItId if a nino is supplied for which there is no persisted match and there is a matching DES business partner record with an mtdItId" in {
      whenDesBusinessPartnerRecordExistsFor(Nino(nino1.value), mtdItId1.value)

      val shouldBeMtdItId = await(service.translateToMtdItId(nino1.value, NinoType.id))
      shouldBeMtdItId.head.underlying shouldBe mtdItId1
    }

    "return None if a nino is supplied for which there is a matching DES business partner record without a mtdItId" in {
      whenDesBusinessPartnerRecordExistsWithoutMtdItIdFor(Nino(nino1.value))
      val shouldBeMtdItId = await(service.translateToMtdItId(nino1.value, "ni"))
      shouldBeMtdItId shouldBe None
    }

    "return None if a nino is supplied for which there is no matching DES business partner record" in {
      whenDesBusinessPartnerRecordDoesNotExist

      val shouldBeMtdItId = await(service.translateToMtdItId(nino1.value, "ni"))
      shouldBeMtdItId shouldBe None
    }
  }

  def serviceWithDurationAndCurrentDate(invitationExpiryDuration: String): InvitationsService =
    new InvitationsService(
      invitationsRepository,
      agentLinkService,
      relationshipsConnector,
      desConnector,
      auditService,
      mockEmailService,
      invitationExpiryDuration,
      metrics)

  "findInvitationsInfoBy" should {

    "return a list of invitation Ids with expiry dates when status is not pending" in {
      when(
        invitationsRepository
          .findInvitationInfoBy(eqs(Arn(arn)), eqs(Seq("nino" -> nino.value)), eqs(Some(Expired)))(any()))
        .thenReturn(Future successful List(
          InvitationInfo(InvitationId("ABBBBBBBBBBCA"), LocalDate.parse("2018-01-01"), Expired),
          InvitationInfo(InvitationId("ABBBBBBBBBBCA"), LocalDate.parse("2017-01-01"), Expired)
        ))

      val result = service.findInvitationsInfoBy(Arn(arn), Seq("nino" -> nino.value), Some(Expired))

      await(result) shouldBe List(
        InvitationInfo(InvitationId("ABBBBBBBBBBCA"), LocalDate.parse("2018-01-01"), Expired),
        InvitationInfo(InvitationId("ABBBBBBBBBBCA"), LocalDate.parse("2017-01-01"), Expired)
      )

    }
  }

  "findAndUpdateExpiredInvitations" should {

    "send an authorisation expired email" when {
      "the expiry date is before the current date" in {

        val invitation = Invitation(
          generate,
          InvitationId.create(arn, mtdItId1.value, Service.PersonalIncomeRecord.id, DateTime.parse("2019-01-01"))('B'),
          Arn(arn),
          Some("personal"),
          Service.PersonalIncomeRecord,
          ClientIdentifier(nino1),
          ClientIdentifier(nino1),
          now().toLocalDate.minusDays(14),
          None,
          None,
          List(StatusChangeEvent(now(), Pending))
        )

        val updatedExpiredInvitation = invitation.copy(events = StatusChangeEvent(now(), Expired) :: invitation.events)

        when(invitationsRepository.findInvitationsBy(any(), any(), any(), eqs(Some(Pending)), any())(any()))
          .thenReturn(Future(List(invitation)))

        when(
          invitationsRepository.update(eqs(invitation), eqs(Expired), any())(any())
        ).thenReturn(Future successful updatedExpiredInvitation)

        when(
          mockEmailService.sendExpiredEmail(eqs(updatedExpiredInvitation))(any())
        ).thenReturn(Future successful ())

        await(service.findAndUpdateExpiredInvitations)

        verify(invitationsRepository).findInvitationsBy(any(), any(), any(), eqs(Some(Pending)), any())(any())
        verify(invitationsRepository).update(eqs(invitation), eqs(Expired), any[DateTime])(any())
        verify(mockEmailService).sendExpiredEmail(eqs(updatedExpiredInvitation))(any())

      }
    }
  }

  private def testInvitationWithStatus(status: InvitationStatus) =
    Invitation(
      generate,
      InvitationId.create(arn, mtdItId1.value, "mtd-sa", DateTime.parse("2001-01-01"))('A'),
      Arn(arn),
      Some("personal"),
      Service.MtdIt,
      mtdItId1,
      ClientIdentifier(nino1),
      LocalDate.now().plusDays(14),
      None,
      None,
      List(StatusChangeEvent(now(), Pending), StatusChangeEvent(now(), status))
    )

  private def testInvitation = testInvitationWithDate(now)

  private def testInvitationWithDate(creationDate: () => DateTime) =
    Invitation(
      generate,
      InvitationId.create(arn, mtdItId1.value, "mtd-sa", DateTime.parse("2001-01-01"))('A'),
      Arn(arn),
      Some("personal"),
      Service.MtdIt,
      ClientIdentifier(mtdItId1),
      ClientIdentifier(nino1),
      LocalDate.now().plusDays(14),
      Some(DetailsForEmail("email@mail.com", "Agent Name", "Client Name")),
      Some("/invitations"),
      List(StatusChangeEvent(creationDate(), Pending))
    )

  private def whenStatusIsChangedTo(status: InvitationStatus): OngoingStubbing[Future[Invitation]] =
    when(invitationsRepository.update(any[Invitation], eqs(status), any[DateTime])(any()))

  private def whenRelationshipIsCreated(invitation: Invitation): OngoingStubbing[Future[Unit]] =
    when(relationshipsConnector.createMtdItRelationship(invitation))

  private def whenAfiRelationshipIsCreated(invitation: Invitation): OngoingStubbing[Future[Unit]] =
    when(relationshipsConnector.createAfiRelationship(eqs(invitation), any[DateTime])(any(), any()))

  private def whenDesBusinessPartnerRecordExistsFor(
    nino: Nino,
    mtdItId: String): OngoingStubbing[Future[Option[BusinessDetails]]] =
    when(desConnector.getBusinessDetails(nino)).thenReturn(
      Future successful Some(
        BusinessDetails(Array(BusinessData(BusinessAddressDetails("postcode", None))), Some(MtdItId(mtdItId)))))

  private def whenDesBusinessPartnerRecordExistsWithoutMtdItIdFor(
    nino: Nino): OngoingStubbing[Future[Option[BusinessDetails]]] =
    when(desConnector.getBusinessDetails(nino)).thenReturn(
      Future successful Some(BusinessDetails(Array(BusinessData(BusinessAddressDetails("postcode", None))), None)))

  private def whenDesBusinessPartnerRecordDoesNotExist: OngoingStubbing[Future[Option[BusinessDetails]]] =
    when(desConnector.getBusinessDetails(nino1)).thenReturn(Future successful None)
}
