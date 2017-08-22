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

package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.AppAndStubs
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

import scala.concurrent.ExecutionContext.Implicits.global

class AuthConnectorISpec extends UnitSpec with AppAndStubs {


  "currentArn" should {
    "return None when the agent has no HMRC-AS-AGENT enrolment" in {
      given()
        .agentAdmin(arn, agentCode)
        .isLoggedIn()
        .andIsNotSubscribedToAgentServices()

      await(newAuthConnector().currentArn()) shouldBe None
    }

    "return the ARN when the agent has an HMRC-AS-AGENT enrolment" in {
      given()
        .agentAdmin(arn, agentCode)
        .isLoggedIn()
        .andIsSubscribedToAgentServices()

      await(newAuthConnector().currentArn()) shouldBe Some(Arn(arn))
    }
  }

  def newAuthConnector() = app.injector.instanceOf(classOf[AuthConnector])

}
