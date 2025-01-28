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

import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar.mock
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

class InvitationsServiceSpec extends UnitSpec with BeforeAndAfterEach {

  val mockRepository: InvitationsRepository = mock[InvitationsRepository]
  val mockRelationshipsConnector: RelationshipsConnector = mock[RelationshipsConnector]
  val mockAnalyticsService: PlatformAnalyticsService = mock[PlatformAnalyticsService]
  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockIfConnector: IfConnector = mock[IfConnector]
  val mockEmailService: EmailService = mock[EmailService]
  val mockAuditService: AuditService = mock[AuditService]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val mockMetrics: Metrics = mock[Metrics]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val service = new InvitationsService(
    mockRepository,
    mockRelationshipsConnector,
    mockAnalyticsService,
    mockDesConnector,
    mockIfConnector,
    mockEmailService,
    mockAuditService,
    mockAppConfig,
    mockMetrics
  )

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

  override def beforeEach(): Unit =
    reset(mockRelationshipsConnector)

  ".findInvitationsBy" should {

    "make a call to ACR when the ACR mongo feature is enabled, and combine the ACR and ACA invitations" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(true)
      when(mockRelationshipsConnector.lookupInvitations(eqTo(Some(Arn("XARN1234567"))), eqTo(Seq()), eqTo(Seq()), eqTo(None))(eqTo(hc)))
        .thenReturn(Future.successful(List(vatInvitation)))
      when(mockRepository.findInvitationsBy(eqTo(Some(Arn("XARN1234567"))), eqTo(Seq()), eqTo(None), eqTo(None), eqTo(None), eqTo(true)))
        .thenReturn(Future.successful(List(vatInvitation)))
      val result = await(service.findInvitationsBy(Some(Arn("XARN1234567")), Seq(), None, None, None))

      result.length shouldBe 2
      verify(mockRelationshipsConnector, times(1)).lookupInvitations(Some(Arn("XARN1234567")), Seq(), Seq(), None)
    }

    "not call ACR when the ACR mongo feature is disabled, and just return ACA invitations" in {
      when(mockAppConfig.acrMongoActivated).thenReturn(false)
      when(mockRepository.findInvitationsBy(eqTo(Some(Arn("XARN1234567"))), eqTo(Seq()), eqTo(None), eqTo(None), eqTo(None), eqTo(false)))
        .thenReturn(Future.successful(List(vatInvitation)))
      val result = await(service.findInvitationsBy(Some(Arn("XARN1234567")), Seq(), None, None, None))

      result.length shouldBe 1
      verify(mockRelationshipsConnector, times(0)).lookupInvitations(Some(Arn("XARN1234567")), Seq(), Seq(), None)
    }
  }
}
