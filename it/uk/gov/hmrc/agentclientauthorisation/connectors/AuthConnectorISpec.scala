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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL

import uk.gov.hmrc.agentclientauthorisation.WSHttp
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, EnrolmentStates}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class AuthConnectorISpec extends UnitSpec with AppAndStubs {

  def hasEnroledAndActived(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] = {
    newAuthConnector().containsEnrolment("IR-SA-AGENT")(_.isActivated)
  }

  "hasActivatedIrSaEnrolment" should {
    "return false if there is no IR-SA-AGENT enrolment" in {
      given()
        .agentAdmin("ABCDEF123456")
        .isLoggedIn()
        .andHasNoIrSaAgentEnrolment()

      await(hasEnroledAndActived) shouldBe false
    }

    "return false if there is a Pending IR-SA-AGENT enrolment" in {
      given()
        .agentAdmin("ABCDEF123456")
        .isLoggedIn()
        .andHasIrSaAgentEnrolment(EnrolmentStates.pending)

      await(hasEnroledAndActived) shouldBe false
    }

    "return true if there is an Activated IR-SA-AGENT enrolment" in {
      given()
        .agentAdmin("ABCDEF123456")
        .isLoggedIn()
        .andHasIrSaAgentEnrolment()

      await(hasEnroledAndActived) shouldBe true
    }

    "return a failed future if any errors happen" in {
      given()
        .agentAdmin("ABCDEF123456").isLoggedIn()
        .andGettingEnrolmentsFailsWith500()

      an[Exception] shouldBe thrownBy {
        await(hasEnroledAndActived)
      }
    }
  }


  "currentAccounts" should {
    "return current accounts" in {
      given()
        .agentAdmin("ABCDEF123456")
        .isLoggedIn()

      await(newAuthConnector().currentAccounts()) shouldBe Accounts(agent = Some(AgentCode("ABCDEF123456")), sa = None)
    }
  }

  "currentUserInfo" should {
    "return accounts and that user has active SA agent enrolment" in {
      given()
        .agentAdmin("ABCDEF123456")
        .isLoggedIn()
        .andHasIrSaAgentEnrolment()

      await(newAuthConnector().currentUserInfo()) shouldBe UserInfo(Accounts(agent = Some(AgentCode("ABCDEF123456")), sa = None), s"http://localhost:$wiremockPort/user-details/id/556737e15500005500eaf68e", true)
    }

    "return accounts and that user does not have active SA agent enrolment" in {
      given()
        .agentAdmin("ABCDEF123456")
        .isLoggedIn()
        .andHasIrSaAgentEnrolment(EnrolmentStates.pending)

      await(newAuthConnector().currentUserInfo()) shouldBe UserInfo(Accounts(agent = Some(AgentCode("ABCDEF123456")), sa = None), s"http://localhost:$wiremockPort/user-details/id/556737e15500005500eaf68e", false)
    }
  }

  def newAuthConnector() = new AuthConnector(new URL(wiremockBaseUrl), WSHttp)

}
