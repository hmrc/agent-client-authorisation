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

import org.mockito.Matchers.{any, eq => eqs}
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AuthConnector, UserInfo}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{SaClientRequest, AgentRequest, AuthActions}
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.Upstream4xxResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AuthActionsSpec extends UnitSpec with MockitoSugar with AuthActions with BeforeAndAfterEach {

  override val authConnector: AuthConnector = mock[AuthConnector]

  "onlyForSaAgents" should {
    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      status(response(onlyForSaAgents)) shouldBe 401
    }

    "return 401 when a non agent user is logged in" in {
      givenClientIsLoggedIn()
      status(response(onlyForSaAgents)) shouldBe 401
    }

    "return 401 when an agent without an activated IR-SA-AGENT enrolment is logged in" in {
      givenAgentWithoutEnrolmentIsLoggedIn()
      status(response(onlyForSaAgents)) shouldBe 401
    }

    "return 200 when an agent with an activated IR-SA-AGENT enrolment is logged in" in {
      givenAgentIsLoggedIn()
      status(response(onlyForSaAgents)) shouldBe 200
    }

    "pass agent details to the block when an agent with an activated IR-SA-AGENT enrolment is logged in" in {
      givenAgentIsLoggedIn()
      var blockCalled = false
      val action = onlyForSaAgents { request =>
        blockCalled = true
        request.agentCode shouldBe AgentCode("54321")
        request.userDetailsLink shouldBe "user-details-link"
        Results.Ok
      }
      await(action(FakeRequest()))
      blockCalled shouldBe true
    }
  }

  "onlyForSaClients" should {
    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      status(response(onlyForSaClients)) shouldBe 401
    }

    "return 401 when a user who has no SA enrolment is logged in" in {
      givenAgentIsLoggedIn()
      status(response(onlyForSaClients)) shouldBe 401
    }

    "return 200 when a user who has an SA enrolment is logged in" in {
      givenClientIsLoggedIn()
      status(response(onlyForSaClients)) shouldBe 200
    }

    "pass client's SA UTR to the block when a user who has an SA enrolment is logged in" in {
      givenClientIsLoggedIn()
      var blockCalled = false
      val action = onlyForSaClients { request =>
        blockCalled = true
        request.saUtr shouldBe SaUtr("1234567890")
        Results.Ok
      }
      await(action(FakeRequest()))
      blockCalled shouldBe true
    }
  }

  "saClientsOrAgents" should {
    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      status(response(saClientsOrAgents)) shouldBe 401
    }

    "return 401 when a user who is not an agent nor has an SA enrolment is logged in" in {
      givenSomeUserIsLoggedIn()
      status(response(saClientsOrAgents)) shouldBe 401
    }

    "return 401 when an agent without an activated IR-SA-AGENT enrolment is logged in" in {
      givenAgentWithoutEnrolmentIsLoggedIn()
      status(response(saClientsOrAgents)) shouldBe 401
    }

    "return 200 when a user who has an SA enrolment is logged in" in {
      givenClientIsLoggedIn()
      status(response(saClientsOrAgents)) shouldBe 200
    }

    "pass client's SA UTR to the block when a user who has an SA enrolment is logged in" in {
      givenClientIsLoggedIn()
      var blockCalled = false
      val action = saClientsOrAgents { request =>
        blockCalled = true
        request.asInstanceOf[SaClientRequest[_]].saUtr shouldBe SaUtr("1234567890")
        Results.Ok
      }
      await(action(FakeRequest()))
      blockCalled shouldBe true
    }

    "return 200 when an agent with an activated IR-SA-AGENT enrolment is logged in" in {
      givenAgentIsLoggedIn()
      status(response(saClientsOrAgents)) shouldBe 200
    }

    "pass agent details to the block when an agent with an activated IR-SA-AGENT enrolment is logged in" in {
      givenAgentIsLoggedIn()
      var blockCalled = false
      val action = saClientsOrAgents { request =>
        blockCalled = true
        request.asInstanceOf[AgentRequest[_]].agentCode shouldBe AgentCode("54321")
        request.asInstanceOf[AgentRequest[_]].userDetailsLink shouldBe "user-details-link"
        Results.Ok
      }
      await(action(FakeRequest()))
      blockCalled shouldBe true
    }
  }

  private def response(actionBuilder: ActionBuilder[Request]): Result = {
    val action = actionBuilder {  Results.Ok }
    await(action(FakeRequest()))
  }

  private def givenAgentIsLoggedIn() = givenUserInfoIs(UserInfo(Accounts(Some(AgentCode("54321")), None), "user-details-link", true))
  private def givenAgentWithoutEnrolmentIsLoggedIn() = givenUserInfoIs(UserInfo(Accounts(Some(AgentCode("54321")), None), "user-details-link", false))
  private def givenClientIsLoggedIn() = givenUserInfoIs(UserInfo(Accounts(None, Some(SaUtr("1234567890"))), "user-details-link", false))
  private def givenSomeUserIsLoggedIn() = givenUserInfoIs(UserInfo(Accounts(None, None), "user-details-link", false))
  private def givenUserIsNotLoggedIn() = whenUserInfoIsAskedFor().thenReturn(Future failed new Upstream4xxResponse("msg", 401, 401))
  private def givenUserInfoIs(userInfo: UserInfo) = whenUserInfoIsAskedFor().thenReturn(Future successful userInfo)
  private def whenUserInfoIsAskedFor() = when(authConnector.currentUserInfo()(any(), any()))

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(authConnector)
  }


}
