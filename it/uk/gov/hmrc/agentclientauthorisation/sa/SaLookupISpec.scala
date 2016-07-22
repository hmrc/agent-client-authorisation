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

package uk.gov.hmrc.agentclientauthorisation.sa

import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, CesaStubs, Resource}
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.test.UnitSpec

class SaLookupISpec extends UnitSpec with AppAndStubs {
  private val me = AgentCode("ABCDEF12345678")

  "GET /sa/lookup/:saUtr/:postcode" should {
    "return name when there is a match" in {
      given().agentAdmin(me).isLoggedIn().andHasIrSaAgentEnrolment()

      val saUtr = SaUtr("1234567890")
      CesaStubs.saTaxpayerExists(saUtr)

      val response = new Resource("/agent-client-authorisation/sa/lookup/1234567890/AA1%201AA", port).get()
      response.status shouldBe 200
      (response.json \ "name").as[String] shouldBe "Mr First Last"
    }

    "return 404 when there is no match" in {
      given().agentAdmin(me).isLoggedIn().andHasIrSaAgentEnrolment()

      val saUtr = SaUtr("1234567890")
      CesaStubs.saTaxpayerExists(saUtr)

      val response = new Resource("/agent-client-authorisation/sa/lookup/1234567890/ZA1%201AA", port).get()
      response.status shouldBe 404
      response.body shouldBe ""
    }

    "return 401 when the logged in user does not have an activated IR-SA-AGENT enrolment" in {
      given().agentAdmin(me).isLoggedIn().andHasNoIrSaAgentEnrolment()

      val saUtr = SaUtr("1234567890")
      CesaStubs.saTaxpayerExists(saUtr)

      val matchResponse = new Resource("/agent-client-authorisation/sa/lookup/1234567890/AA1%201AA", port).get()
      matchResponse.status shouldBe 401
      matchResponse.body shouldBe ""

      val noMatchResponse = new Resource("/agent-client-authorisation/sa/lookup/1234567890/ZA1%201AA", port).get()
      noMatchResponse.status shouldBe 401
      noMatchResponse.body shouldBe ""
    }
  }
}
