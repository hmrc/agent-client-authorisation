/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.Mockito.{times, verify, when}
import org.apache.pekko.Done
import org.scalatest.Assertion
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, IfConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, Invitation, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Service.Vat
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, InvitationId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.agentclientauthorisation.model.DetailsForEmail
import uk.gov.hmrc.agentclientauthorisation.model.AuthorisationRequest
import uk.gov.hmrc.agentclientauthorisation.model.Invitation.toInvitationInfo
import uk.gov.hmrc.agentclientauthorisation.support.{ResettingMockitoSugar, TestData}
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId

import scala.concurrent.duration.DurationInt

class InvitationsServiceSpec extends UnitSpec with ResettingMockitoSugar with TestData {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockInvitationsRepository: InvitationsRepository = mock[InvitationsRepository]
  val mockRelationshipsConnector: RelationshipsConnector = resettingMock[RelationshipsConnector]
  val mockAnalyticsService: PlatformAnalyticsService = mock[PlatformAnalyticsService]
  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockIfConnector: IfConnector = mock[IfConnector]
  val mockEmailService: EmailService = mock[EmailService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockAppConfig: AppConfig = resettingMock[AppConfig]
  val mockMetrics: Metrics = mock[Metrics]

  val service = new InvitationsService(
    mockInvitationsRepository,
    mockRelationshipsConnector,
    mockAnalyticsService,
    mockDesConnector,
    mockIfConnector,
    mockEmailService,
    mockAuditService,
    mockAppConfig,
    mockMetrics
  )

  val vatClientId: ClientId = ClientIdentifier("123456789", "vrn")
  val vatInvitation: Invitation = Invitation(
    invitationId = InvitationId("123"),
    arn = Arn("XARN1234567"),
    clientType = Some("personal"),
    service = Vat,
    clientId = ClientIdentifier("123456789", "vrn"),
    suppliedClientId = ClientIdentifier("234567890", "vrn"),
    expiryDate = LocalDate.parse("2020-01-01"),
    detailsForEmail = None,
    isRelationshipEnded = true,
    relationshipEndedBy = Some("Me"),
    events = List(
      StatusChangeEvent(LocalDateTime.parse("2020-02-02T00:00:00"), Pending),
      StatusChangeEvent(LocalDateTime.parse("2020-03-03T00:00:00"), Accepted)
    ),
    clientActionUrl = None,
    fromAcr = true
  )

  def compareInvitationsIgnoringTimestamps(result: Invitation, expected: Invitation): Assertion = {
    result.events.size shouldBe expected.events.size
    result.events.zip(expected.events).foreach { case (x, y) =>
      x.status shouldBe y.status
    }
    result.copy(events = List.empty) shouldBe expected.copy(events = List.empty)
  }

  "clientsReceived" should {
    "make a call to ACR when the ACR mongo feature is enabled, and combine the ACR and ACA invitations" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(true)
      when(mockRelationshipsConnector.lookupInvitations(None, Seq(Vat), Seq(vatClientId.value), Some(Accepted))(hc))
        .thenReturn(Future.successful(List(vatInvitation)))
      when(mockInvitationsRepository.findInvitationsBy(None, Seq(Vat), Some(vatClientId.value), Some(Accepted), None, within30Days = true))
        .thenReturn(Future.successful(List(vatInvitation)))

      val result = await(service.clientsReceived(Seq(Vat), vatClientId, Some(Accepted)))

      result.length shouldBe 2
      verify(mockRelationshipsConnector, times(1)).lookupInvitations(None, Seq(Vat), Seq(vatClientId.value), Some(Accepted))
    }

    "not call ACR when the ACR mongo feature is disabled, and just return ACA invitations" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(false)
      when(mockInvitationsRepository.findInvitationsBy(None, Seq(Vat), Some(vatClientId.value), None, None, within30Days = false))
        .thenReturn(Future.successful(List(vatInvitation)))
      val result = await(service.clientsReceived(Seq(Vat), vatClientId, None))

      result.length shouldBe 1
      verify(mockRelationshipsConnector, times(0)).lookupInvitations(None, Seq(Vat), Seq(vatClientId.value), None)
    }
  }

  ".findInvitationsBy" should {
    "make a call to ACR when the ACR mongo feature is enabled, and combine the ACR and ACA invitations" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(true)
      when(mockRelationshipsConnector.lookupInvitations(Some(Arn("XARN1234567")), Seq(), Seq(), None)(hc))
        .thenReturn(Future.successful(List(vatInvitation)))
      when(mockInvitationsRepository.findInvitationsBy(Some(Arn("XARN1234567")), Seq(), None, None, None, within30Days = true))
        .thenReturn(Future.successful(List(vatInvitation)))
      val result = await(service.findInvitationsBy(Some(Arn("XARN1234567")), Seq(), None, None, None))

      result.length shouldBe 2
      verify(mockRelationshipsConnector, times(1)).lookupInvitations(Some(Arn("XARN1234567")), Seq(), Seq(), None)
    }

    "not call ACR when the ACR mongo feature is disabled, and just return ACA invitations" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(false)
      when(mockInvitationsRepository.findInvitationsBy(Some(Arn("XARN1234567")), Seq(), None, None, None, within30Days = false))
        .thenReturn(Future.successful(List(vatInvitation)))
      val result = await(service.findInvitationsBy(Some(Arn("XARN1234567")), Seq(), None, None, None))

      result.length shouldBe 1
      verify(mockRelationshipsConnector, times(0)).lookupInvitations(Some(Arn("XARN1234567")), Seq(), Seq(), None)
    }
  }

  ".findInvitationsInfoBy" should {
    "make a call to ACR when the ACR mongo feature is enabled, and combine the ACR and ACA invitation info" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(true)
      when(mockRelationshipsConnector.lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), None)(hc))
        .thenReturn(Future.successful(List(vatInvitation)))
      when(mockInvitationsRepository.findInvitationInfoBy(Arn("XARN1234567"), Seq(("HMRC-MTD-VAT", "vrn", "123456789")), None))
        .thenReturn(Future.successful(List(toInvitationInfo(vatInvitation.copy(fromAcr = false)))))
      val result = await(service.findInvitationsInfoBy(Arn("XARN1234567"), Seq(("HMRC-MTD-VAT", "vrn", "123456789")), None))

      result.length shouldBe 2
      verify(mockRelationshipsConnector, times(1)).lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), None)
    }

    "not call ACR when the ACR mongo feature is disabled, and just return ACA invitation info" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(false)
      when(mockInvitationsRepository.findInvitationInfoBy(Arn("XARN1234567"), Seq(("HMRC-MTD-VAT", "vrn", "123456789")), None))
        .thenReturn(Future.successful(List(toInvitationInfo(vatInvitation.copy(fromAcr = false)))))
      val result = await(service.findInvitationsInfoBy(Arn("XARN1234567"), Seq(("HMRC-MTD-VAT", "vrn", "123456789")), None))

      result.length shouldBe 1
      verify(mockRelationshipsConnector, times(0)).lookupInvitations(Some(Arn("XARN1234567")), Seq(Vat), Seq("123456789"), None)
    }
  }

  ".create" when {
    "given valid input and all external calls are successful" should {
      "return a new ACA invitation if acrMongoActivated is switched off" in {
        val invitation = invitationPending.copy(detailsForEmail = Some(DetailsForEmail("agency@email.com", "Prestige WorldWide", "Troy Barnes")))

        val arn = invitation.arn
        val nino = invitation.clientId
        val regime = invitation.service
        val detailsForEmail = invitation.detailsForEmail
        val expected = invitation.copy(events = List(StatusChangeEvent(LocalDateTime.now, Pending)))

        when(mockAppConfig.acrMongoActivated)
          .thenReturn(false)
        when(mockEmailService.createDetailsForEmail(arn, nino, regime))
          .thenReturn(Future.successful(detailsForEmail.get))
        when(mockInvitationsRepository.create(arn, invitation.clientType, regime, nino, nino, detailsForEmail, None))
          .thenReturn(Future.successful(invitation))
        when(mockAnalyticsService.reportSingleEventAnalyticsRequest(invitation))
          .thenReturn(Future.successful(Done))

        val result = await(service.create(arn, invitation.clientType, regime, nino, nino, None))

        compareInvitationsIgnoringTimestamps(result, expected)
      }

      "return an ACR invitation if acrMongoActivated is switched on" in {
        val invitation = invitationPending.copy(detailsForEmail = Some(DetailsForEmail("agency@email.com", "Prestige WorldWide", "Troy Barnes")))

        val arn = invitation.arn
        val mtdId = invitation.clientId
        val regime = invitation.service
        val detailsForEmail = invitation.detailsForEmail.get
        val invitationId = "ABC123"
        val expected = invitation.copy(
          invitationId = InvitationId(invitationId),
          events = List(StatusChangeEvent(LocalDateTime.now, Pending)),
          expiryDate = LocalDate.now.plusDays(21)
        )

        val authorisationRequest = AuthorisationRequest(
          invitation.suppliedClientId.value,
          invitation.suppliedClientId.typeId,
          detailsForEmail.clientName,
          regime.id,
          invitation.clientType
        )

        when(mockAppConfig.acrMongoActivated)
          .thenReturn(true)
        when(mockEmailService.createDetailsForEmail(arn, mtdId, regime))
          .thenReturn(Future.successful(detailsForEmail))
        when(mockAppConfig.invitationExpiringDuration)
          .thenReturn(21.days)
        when(mockRelationshipsConnector.sendAuthorisationRequest(arn.value, authorisationRequest))
          .thenReturn(Future.successful(invitationId))

        val result = await(service.create(arn, invitation.clientType, regime, mtdId, invitation.suppliedClientId, None))

        // id is created inside service method, no way for us to know what it is beforehand
        compareInvitationsIgnoringTimestamps(result, expected.copy(_id = result._id))
      }
    }
  }
}
