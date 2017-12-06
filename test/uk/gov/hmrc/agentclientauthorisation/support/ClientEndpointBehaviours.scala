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

package uk.gov.hmrc.agentclientauthorisation.support

import org.joda.time.DateTime.now
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientInvitationsController
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future


trait ClientEndpointBehaviours extends TransitionInvitation with Eventually {
  this: UnitSpec with ResettingMockitoSugar =>

  override implicit val patienceConfig = PatienceConfig(scaled(Span(5, Seconds)), scaled(Span(500, Millis)))

  val invitationsService: InvitationsService = resettingMock[InvitationsService]
  val authConnector: AuthConnector = resettingMock[AuthConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)

  def controller: ClientInvitationsController

  def invitationDbId: String
  def invitationId: InvitationId

  def arn: Arn

  def generator: Generator

  def whenFindingAnInvitation: OngoingStubbing[Future[Option[Invitation]]] = {
    when(invitationsService.findInvitation(eqs(invitationId))(any()))
  }

  def noInvitation: Future[None.type] = Future successful None

  def anInvitation(nino: Nino) = Invitation(BSONObjectID(invitationDbId), invitationId, arn, "MTDITID", mtdItId1.value, "A11 1AA", nino.value, "ni",
    List(StatusChangeEvent(now(), Pending)))

  def aFutureOptionInvitation(): Future[Option[Invitation]] =
    Future successful Some(anInvitation(nino1))

  def whenInvitationIsAccepted: OngoingStubbing[Future[Either[String, Invitation]]] =
    when(invitationsService.acceptInvitation(any[Invitation])(any[HeaderCarrier], any()))

  def whenInvitationIsRejected: OngoingStubbing[Future[Either[String, Invitation]]] =
    when(invitationsService.rejectInvitation(any[Invitation])(any()))

  def whenClientReceivedInvitation: OngoingStubbing[Future[Seq[Invitation]]] =
    when(invitationsService.clientsReceived(eqs("HMRC-MTD-IT"), eqs(mtdItId1), eqs(None))(any()))

  def assertCreateRelationshipEvent(event: DataEvent) = {
    event.auditSource shouldBe "agent-client-authorisation"
    event.auditType shouldBe "AgentClientRelationshipCreated"
    val details = event.detail.toSeq
    details should contain allOf(
      "invitationId" -> invitationId.value,
      "agentReferenceNumber" -> arn.value,
      "regimeId" -> mtdItId1.value,
      "regime" -> "HMRC-MTD-IT")
  }

  def verifyAgentClientRelationshipCreatedAuditEvent() = {
    eventually {
      val argumentCaptor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])
      verify(auditConnector).sendEvent(argumentCaptor.capture())(any(), any())
      val event: DataEvent = argumentCaptor.getValue
      assertCreateRelationshipEvent(event)
    }
  }

  def verifyNoAuditEventSent() = {
    Thread.sleep(scaled(Span(1000, Millis)).millisPart)
    verify(auditConnector, never()).sendEvent(any(classOf[DataEvent]))(any(), any())
  }
}

