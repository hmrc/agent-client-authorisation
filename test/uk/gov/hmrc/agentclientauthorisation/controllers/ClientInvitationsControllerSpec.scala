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
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.{HeaderCarrier, Upstream4xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class ClientInvitationsControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ClientEndpointBehaviours {
  implicit val hc = HeaderCarrier()

  val controller = new ClientInvitationsController(invitationsRepository, authConnector, agenciesFakeConnector)

  val invitationId = BSONObjectID.generate.stringify
  val clientRegimeId = "clientId"


  "Accepting an invitation" should {
    behave like clientStatusChangeEndpoint(controller.acceptInvitation(clientRegimeId, invitationId), Accepted)
  }

  "Rejecting an invitation" should {
    behave like clientStatusChangeEndpoint(controller.acceptInvitation(clientRegimeId, invitationId), Rejected)
  }
}

  trait ClientEndpointBehaviours {
    this: UnitSpec with MockitoSugar =>

    val invitationsRepository = mock[InvitationsRepository]
    val authConnector = mock[AuthConnector]
    val agenciesFakeConnector = mock[AgenciesFakeConnector]

    def controller: ClientInvitationsController
    def clientRegimeId: String
    def invitationId: String

    def clientStatusChangeEndpoint(endpoint: => Action[AnyContent], status: InvitationStatus) {

       "Return no content" in {
        val request = FakeRequest()
        whenAuthIsCalled thenReturn aClientUser()
        whenFindingAnInvitation thenReturn anInvitation()
        whenUpdatingAnInvitationTo(status) thenReturn anUpdatedInvitation()

        val response = await(endpoint(request))

        response.header.status shouldBe 204
      }

      "Return not found when the invitation doesn't exist" in {
        val request = FakeRequest()
        whenAuthIsCalled thenReturn aClientUser()
        whenFindingAnInvitation thenReturn noInvitation

        val response = await(controller.acceptInvitation(clientRegimeId, invitationId)(request))

        response.header.status shouldBe 404
      }

      "Return unauthorised when the user is not logged in to MDTP" in {
        val request = FakeRequest()
        whenAuthIsCalled thenReturn userIsNotLoggedIn

        val response = await(controller.acceptInvitation(clientRegimeId, invitationId)(request))

        response.header.status shouldBe 401
      }

      "Return forbidden" when {
        "the invitation is for a different client" in {
          pending
        }

        "the invitation has been cancelled in" in {
          val request = FakeRequest()
          whenAuthIsCalled thenReturn aClientUser()
          whenFindingAnInvitation thenReturn anInvitationWithStatus(Cancelled)

          val response = await(endpoint(request))

          response.header.status shouldBe 403
        }

        "the invitation has been accepted in" in {
          val request = FakeRequest()
          whenAuthIsCalled thenReturn aClientUser()
          whenFindingAnInvitation thenReturn anInvitationWithStatus(Accepted)

          val response = await(endpoint(request))

          response.header.status shouldBe 403
        }

        "the invitation has been rejected in" in {
          val request = FakeRequest()
          whenAuthIsCalled thenReturn aClientUser()
          whenFindingAnInvitation thenReturn anInvitationWithStatus(Rejected)

          val response = await(endpoint(request))

          response.header.status shouldBe 403
        }
      }
    }


    def whenUpdatingAnInvitationTo(status: InvitationStatus): OngoingStubbing[Future[Invitation]] = {
      when(invitationsRepository.update(BSONObjectID(invitationId), status))
    }

    def whenFindingAnInvitation: OngoingStubbing[Future[Option[Invitation]]] = {
      when(invitationsRepository.findById(BSONObjectID(invitationId)))
    }

    def whenAuthIsCalled: OngoingStubbing[Future[Accounts]] = {
      when(authConnector.currentAccounts()(any[HeaderCarrier], any[ExecutionContext]))
    }

    def noInvitation = Future successful None
    def anInvitationWithStatus(status: InvitationStatus): Future[Option[Invitation]] =
      Future successful Some(Invitation(BSONObjectID(invitationId), Arn("12345"), "mtd-sa", clientRegimeId, "A11 1AA",
        List(StatusChangeEvent(now(), Pending), StatusChangeEvent(now(), status))))

    def anInvitation(): Future[Option[Invitation]] =
      Future successful Some(Invitation(BSONObjectID(invitationId), Arn("12345"), "mtd-sa", clientRegimeId, "A11 1AA",
           List(StatusChangeEvent(now(), Pending))))

    def anUpdatedInvitation(): Future[Invitation] =
      anInvitation() map (_.get)

    def userIsNotLoggedIn = Future failed Upstream4xxResponse("Not logged in", 401, 401)

    def aClientUser() =
      Future successful Accounts(None, Some(SaUtr(clientRegimeId)))
}
