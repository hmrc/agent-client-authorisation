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
import org.mockito.Matchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientInvitationsController
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future


trait ClientEndpointBehaviours extends TransitionInvitation {
  this: UnitSpec with MockitoSugar with BeforeAndAfterEach =>

  val invitationsService: InvitationsService = mock[InvitationsService]
  val authConnector: AuthConnector = mock[AuthConnector]

  def controller: ClientInvitationsController

  def invitationId: String

  def arn: Arn

  def generator: Generator

  override def beforeEach(): Unit = {
    reset(invitationsService, authConnector)
  }

  def whenFindingAnInvitation: OngoingStubbing[Future[Option[Invitation]]] = {
    when(invitationsService.findInvitation(eqs(invitationId))(any()))
  }

  def noInvitation: Future[None.type] = Future successful None

  def anInvitation(nino: Nino) = Invitation(BSONObjectID(invitationId), arn, "MTDITID", mtdItId1.value, "A11 1AA", nino.value, "ni",
    List(StatusChangeEvent(now(), Pending)))

  def aFutureOptionInvitation(): Future[Option[Invitation]] =
    Future successful Some(anInvitation(nino1))

  def whenInvitationIsAccepted: OngoingStubbing[Future[Either[String, Invitation]]] =
    when(invitationsService.acceptInvitation(any[Invitation])(any[HeaderCarrier], any()))

  def whenInvitationIsRejected: OngoingStubbing[Future[Either[String, Invitation]]] =
    when(invitationsService.rejectInvitation(any[Invitation])(any()))

  def whenClientReceivedInvitation: OngoingStubbing[Future[Seq[Invitation]]] =
    when(invitationsService.clientsReceived(eqs("HMRC-MTD-IT"), eqs(mtdItId1), eqs(None))(any()))
}
