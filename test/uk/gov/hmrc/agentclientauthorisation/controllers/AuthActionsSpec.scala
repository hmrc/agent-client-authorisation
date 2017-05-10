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

package uk.gov.hmrc.agentclientauthorisation.controllers

import org.mockito.Mockito
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.mvc._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.connectors.{ AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, AuthMocking, ResettingMockitoSugar}
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.play.test.UnitSpec


class AuthActionsSpec extends UnitSpec with MockitoSugar with AuthActions with AuthMocking with ResettingMockitoSugar with BeforeAndAfterEach with AkkaMaterializerSpec {

  override val generator = new Generator()

  override val authConnector: AuthConnector = resettingMock[AuthConnector]

  "onlyForAgents" should {

    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      response(onlyForAgents) shouldBe GenericUnauthorized
    }

    "return 403 when a non agent user is logged in" in {
      givenClientIsLoggedIn()
      response(onlyForAgents) shouldBe AgentNotSubscribed
    }

    "return 403 when an agent without a MTD agency record is logged in" in {
      givenAgentWithoutRecordIsLoggedIn()
      response(onlyForAgents) shouldBe AgentNotSubscribed
    }

    "return 200 when an agent with an MTD agency record is logged in" in {
      givenAgentIsLoggedIn()
      val result = response(onlyForAgents)
      status(result) shouldBe 200
    }

    "pass agent details to the block when an agent with an MTD agency record is logged in" in {
      givenAgentIsLoggedIn()
      var blockCalled = false
      val action = onlyForAgents { request =>
        blockCalled = true
        request.arn shouldBe Arn("12345")
        Results.Ok
      }
      await(action(FakeRequest()))
      blockCalled shouldBe true
    }
  }

  "onlyForClients" should {
    "return 401 when invoked by not logged in user" in {
      givenUserIsNotLoggedIn()
      response(onlyForClients) shouldBe GenericUnauthorized
    }

    "return 403 when a user who has no MTD enrolment is logged in" in {
      pending // reinstate when client validation is implemented
      givenNonMTDClientIsLoggedIn()
      response(onlyForClients) shouldBe ClientRegistrationNotFound
    }

    "return 200 when a user is logged in" in {
      givenClientIsLoggedIn()
      status(response(onlyForClients)) shouldBe 200
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
