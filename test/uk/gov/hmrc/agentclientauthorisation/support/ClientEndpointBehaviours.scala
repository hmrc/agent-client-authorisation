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
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientInvitationsController
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


trait ClientEndpointBehaviours extends TransitionInvitation {
  this: UnitSpec with MockitoSugar with BeforeAndAfterEach =>

  val invitationsService = mock[InvitationsService]
  val authConnector = mock[AuthConnector]
  val agenciesFakeConnector = mock[AgenciesFakeConnector]

  def controller: ClientInvitationsController

  def clientId: String

  def invitationId: String

  def saUtr: String

  def arn: Arn

  override def beforeEach(): Unit = {
    reset(invitationsService, authConnector, agenciesFakeConnector)
  }

  def clientStatusChangeEndpoint(toStatus: InvitationStatus, endpoint: => Action[AnyContent], action: => OngoingStubbing[Future[Either[String, Invitation]]]) {

    "Return no content" in {
      val request = FakeRequest()
      userIsLoggedIn
      val invitation = anInvitation()
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      action thenReturn (Future successful Right(transitionInvitation(invitation, toStatus)))

      val response = await(endpoint(request))

      response.header.status shouldBe 204
    }

    "Return not found when the invitation doesn't exist" in {
      val request = FakeRequest()
      userIsLoggedIn
      whenFindingAnInvitation thenReturn noInvitation

      val response = await(endpoint(request))

      response shouldBe InvitationNotFound
    }

    "Return unauthorised when the user is not logged in to MDTP" in {
      val request = FakeRequest()
      whenAuthIsCalled thenReturn userIsNotLoggedIn

      val response = await(endpoint(request))

      response shouldBe GenericUnauthorized
    }

    "Return forbidden" when {
      "the invitation is for a different client" in {
        val request = FakeRequest()
        whenAuthIsCalled thenReturn aClientUser()
        whenMtdClientIsLookedUp thenReturn aMtdUser("anotherClient")
        val invitation = anInvitation()
        whenFindingAnInvitation thenReturn (Future successful Some(invitation))
        action thenReturn (Future successful Right(transitionInvitation(invitation, toStatus)))

        val response = await(endpoint(request))

        response shouldBe NoPermissionOnClient
      }

      "the invitation cannot be actioned" in {
        val request = FakeRequest()
        userIsLoggedIn
        whenFindingAnInvitation thenReturn aFutureOptionInvitation()
        action thenReturn (Future successful Left("failure message"))

        val response = await(endpoint(request))

        response shouldBe invalidInvitationStatus("failure message")
      }
    }
  }


  def whenFindingAnInvitation: OngoingStubbing[Future[Option[Invitation]]] = {
    when(invitationsService.findInvitation(invitationId))
  }

  def userIsLoggedIn = {
    whenAuthIsCalled thenReturn aClientUser()
    whenMtdClientIsLookedUp thenReturn aMtdUser()
  }

  def whenAuthIsCalled: OngoingStubbing[Future[Accounts]] = {
    when(authConnector.currentAccounts()(any[HeaderCarrier], any[ExecutionContext]))
  }

  def whenMtdClientIsLookedUp = {
    when(agenciesFakeConnector.findClient(eqs(SaUtr(saUtr)))(any[HeaderCarrier], any[ExecutionContext]))
  }

  def noInvitation = Future successful None

  def anInvitation() = Invitation(BSONObjectID(invitationId), arn, "mtd-sa", clientId, "A11 1AA",
    List(StatusChangeEvent(now(), Pending)))

  def aFutureOptionInvitation(): Future[Option[Invitation]] =
    Future successful Some(anInvitation())

  def anUpdatedInvitation(): Future[Invitation] =
    aFutureOptionInvitation() map (_.get)

  def userIsNotLoggedIn = Future failed Upstream4xxResponse("Not logged in", 401, 401)

  def anException = Future failed Upstream5xxResponse("Service failed", 500, 500)

  def aClientUser() =
    Future successful Accounts(None, Some(SaUtr(saUtr)))

  def aMtdUser(clientId: String = clientId) =
    Future successful Some(MtdClientId(clientId))

  def whenInvitationIsAccepted = when(invitationsService.acceptInvitation(any[Invitation])(any[HeaderCarrier]))

  def whenInvitationIsRejected = when(invitationsService.rejectInvitation(any[Invitation]))
}
