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

import org.mockito.Matchers.{any, anyString}
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsValue
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model.AgentClientAuthorisationHttpRequest
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.agentclientauthorisation.sa.services.SaLookupService
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorisationRequestControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  val repository = mock[AuthorisationRequestRepository]
  val saLookupService = mock[SaLookupService]
  val authConnector = mock[AuthConnector]
  val controller = new AuthorisationRequestController(repository, saLookupService, authConnector)

  implicit val hc = HeaderCarrier()

  val agentCode = AgentCode("123456789012")

  override protected def beforeEach() = {
    super.beforeEach()
    reset(repository)
    reset(saLookupService)
  }


  "createRequest" should {
    "return a 403 when the UTR and postcode don't match" in {
      givenAgentIsLoggedIn()
      when(saLookupService.utrAndPostcodeMatch(any[SaUtr], anyString)(any[HeaderCarrier])).thenReturn(Future.successful(false))

      val body: JsValue = AgentClientAuthorisationHttpRequest.format.writes(
        AgentClientAuthorisationHttpRequest(agentCode, SaUtr("54321"), "BB2 2BB"))

      val result: Result = await(controller.createRequest()(FakeRequest().withBody(body)))

      status(result) shouldBe 403
    }

    "propagate exceptions when the repository fails" in {
      givenAgentIsLoggedIn()
      when(saLookupService.utrAndPostcodeMatch(any[SaUtr], anyString)(any[HeaderCarrier])).thenReturn(Future.successful(true))
      when(repository.create(any[AgentCode], any[SaUtr])).thenReturn(Future failed new RuntimeException("dummy exception"))

      val body: JsValue = AgentClientAuthorisationHttpRequest.format.writes(
        AgentClientAuthorisationHttpRequest(agentCode, SaUtr("54321"), "AA1 1AA"))

      intercept[RuntimeException] {
        await(controller.createRequest()(FakeRequest().withBody(body)))
      }.getMessage shouldBe "dummy exception"
    }
  }

  def givenAgentIsLoggedIn(): Unit = {
    when(authConnector.currentAccounts()(any(), any())).thenReturn(Future successful Accounts(Some(agentCode), None))
  }
}
