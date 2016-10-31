/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.DateTime.now
import org.mockito.Matchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsArray
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ClientInvitationsControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ClientEndpointBehaviours {

  val controller = new ClientInvitationsController(invitationsService, authConnector, agenciesFakeConnector)

  val invitationId = BSONObjectID.generate.stringify
  val clientId = "clientId"
  val saUtr = "saUtr"
  val arn: Arn = Arn("12345")


  "Accepting an invitation" should {
    behave like clientStatusChangeEndpoint(
      controller.acceptInvitation(clientId, invitationId),
      whenInvitationIsAccepted
    )

    "not change the invitation status if relationship creation fails" in {
      pending
    }
  }


  "Rejecting an invitation" should {
    behave like clientStatusChangeEndpoint(
      controller.rejectInvitation(clientId, invitationId),
      whenInvitationIsRejected
    )
  }


  "getInvitationsForClient" should {

    "return 200 and an empty list when there are no invitations for the client" in {
      val controller = new ClientInvitationsController(invitationsService, authConnector, agenciesFakeConnector)

      whenAuthIsCalled.thenReturn(Future successful Accounts(None, Some(SaUtr(saUtr))))
      whenMtdClientIsLookedUp.thenReturn(Future successful Some(MtdClientId(clientId)))

      when(invitationsService.list("mtd-sa", clientId)).thenReturn(Future successful Nil)

      val request = FakeRequest()

      val result: Result = await(controller.getInvitations(clientId)(request))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations") shouldBe JsArray()
    }
  }
}

  trait ClientEndpointBehaviours {
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

    def clientStatusChangeEndpoint(endpoint: => Action[AnyContent], action: => OngoingStubbing[Future[Boolean]]) {

       "Return no content" in {
        val request = FakeRequest()
        userIsLoggedIn
        whenFindingAnInvitation thenReturn anInvitation()
        action thenReturn (Future successful true)

        val response = await(endpoint(request))

        response.header.status shouldBe 204
      }

      "Return not found when the invitation doesn't exist" in {
        val request = FakeRequest()
        userIsLoggedIn
        whenFindingAnInvitation thenReturn noInvitation

        val response = await(endpoint(request))

        response.header.status shouldBe 404
      }

      "Return unauthorised when the user is not logged in to MDTP" in {
        val request = FakeRequest()
        whenAuthIsCalled thenReturn userIsNotLoggedIn

        val response = await(endpoint(request))

        response.header.status shouldBe 401
      }

      "Return forbidden" when {
        "the invitation is for a different client" in {
          val request = FakeRequest()
          whenAuthIsCalled thenReturn aClientUser()
          whenMtdClientIsLookedUp thenReturn aMtdUser("anotherClient")
          whenFindingAnInvitation thenReturn anInvitation
          action thenReturn (Future successful true)

          val response = await(endpoint(request))

          response.header.status shouldBe 403
        }

        "the invitation cannot be actioned" in {
          val request = FakeRequest()
          userIsLoggedIn
          whenFindingAnInvitation thenReturn anInvitation
          action thenReturn (Future successful false)

          val response = await(endpoint(request))

          response.header.status shouldBe 403
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

    def anInvitation(): Future[Option[Invitation]] =
      Future successful Some(Invitation(BSONObjectID(invitationId), arn, "mtd-sa", clientId, "A11 1AA",
           List(StatusChangeEvent(now(), Pending))))

    def anUpdatedInvitation(): Future[Invitation] =
      anInvitation() map (_.get)

    def userIsNotLoggedIn = Future failed Upstream4xxResponse("Not logged in", 401, 401)
    def anException = Future failed Upstream5xxResponse("Service failed", 500, 500)

    def aClientUser() =
      Future successful Accounts(None, Some(SaUtr(saUtr)))

    def aMtdUser(clientId: String = clientId) =
      Future successful Some(MtdClientId(clientId))

    def whenInvitationIsAccepted = when(invitationsService.acceptInvitation(any[Invitation])(any[HeaderCarrier]))
    def whenInvitationIsRejected = when(invitationsService.rejectInvitation(any[Invitation])(any[HeaderCarrier]))
}
