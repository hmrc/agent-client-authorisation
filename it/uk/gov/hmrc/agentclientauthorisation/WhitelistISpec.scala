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

package uk.gov.hmrc.agentclientauthorisation

import java.util.Base64

import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.test.UnitSpec

class WhitelistISpec extends UnitSpec with MongoAppAndStubs {

  val agentCode = AgentCode("ABCDEF123456")
  val arn = Arn("A11111A")

  override protected def additionalConfiguration = super.additionalConfiguration ++ Map(
    "microservice.whitelist.enabled" -> "true",
    "microservice.whitelist.ips" -> Base64.getEncoder.encodeToString("192.168.1.2,192.168.1.3".getBytes))


  "A service endpoint" should {
    "respond with NOT_IMPLEMENTED if whitelist is enabled and there is no IP address in header" in {
      givenLoggedInAgentIsAuthorised()

      authResponseFor(arn, None).status shouldBe 501
    }

    "respond with forbidden if whitelist is enabled and there is an IP address in header that is not on the list" in {
      givenLoggedInAgentIsAuthorised()

      authResponseFor(arn, Some("192.168.1.1")).status shouldBe 403
    }

    "be enabled if whitelist is enabled and there is an IP address in header that is on the list" in {
      givenLoggedInAgentIsAuthorised()

      authResponseFor(arn, Some("192.168.1.2")).status shouldBe 200
      authResponseFor(arn, Some("192.168.1.3")).status shouldBe 200
    }
  }

  "ping" should {
    "be available regardless of IP address in the header" in {
      new Resource("/ping/ping", port).get().status shouldBe 200
    }
  }

  "admin details" should {
    "be available regardless of IP address in the header" in {
      new Resource("/admin/details", port).get().status shouldBe 200
    }
  }

  "metrics" should {
    "be available regardless of IP address in the header" in {
      new Resource("/admin/metrics", port).get().status shouldBe 200
    }
  }


  def givenLoggedInAgentIsAuthorised(): Unit = {
    given()
        .agentAdmin(arn, agentCode)
        .isLoggedIn()
        .andHasMtdBusinessPartnerRecord()
  }

  def authResponseFor(arn: Arn, trueClientIp: Option[String]): HttpResponse =
    new Resource(s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent", port)
      .get()(HeaderCarrier(trueClientIp = trueClientIp))

}
