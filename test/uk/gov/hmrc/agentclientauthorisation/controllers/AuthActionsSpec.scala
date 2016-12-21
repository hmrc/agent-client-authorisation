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

import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, AuthMocking, ResettingMockitoSugar}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.test.UnitSpec


class AuthActionsSpec extends UnitSpec with MockitoSugar with AuthActions with AuthMocking with ResettingMockitoSugar with BeforeAndAfterEach with AkkaMaterializerSpec {

  override val authConnector: AuthConnector = resettingMock[AuthConnector]
  override val agenciesFakeConnector: AgenciesFakeConnector = resettingMock[AgenciesFakeConnector]

  "onlyForSaAgents" should {

    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      response(onlyForSaAgents) shouldBe GenericUnauthorized
    }

    "return 403 when a non agent user is logged in" in {
      givenClientIsLoggedIn()
      response(onlyForSaAgents) shouldBe NotAnAgent
    }

    "return 403 when an agent without a MTD agency record is logged in" in {
      givenAgentWithoutRecordIsLoggedIn()
      response(onlyForSaAgents) shouldBe AgentRegistrationNotFound
    }

    "return 200 when an agent with an MTD agency record is logged in" in {
      givenAgentIsLoggedIn()
      val result = response(onlyForSaAgents)
      status(result) shouldBe 200
    }

    "pass agent details to the block when an agent with an MTD agency record is logged in" in {
      givenAgentIsLoggedIn()
      var blockCalled = false
      val action = onlyForSaAgents { request =>
        blockCalled = true
        request.arn shouldBe Arn("12345")
        Results.Ok
      }
      await(action(FakeRequest()))
      blockCalled shouldBe true
    }
  }

  "onlyForSaClients" should {
    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      response(onlyForSaClients) shouldBe GenericUnauthorized
    }

    "return 403 when a user who has no MTD enrolment is logged in" in {
      givenNonMTDClientIsLoggedIn()
      response(onlyForSaClients) shouldBe ClientRegistrationNotFound
    }

    "return 403 when a user who has no SA enrolment is logged in" in {
      givenClientIsLoggedInWithNoSAAccount()
      response(onlyForSaClients) shouldBe SaEnrolmentNotFound
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

  private def response(actionBuilder: ActionBuilder[Request]): Result = {
    val action = actionBuilder {  Results.Ok }
    await(action(FakeRequest()))
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    Mockito.reset(authConnector)
  }

}
