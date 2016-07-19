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

package uk.gov.hmrc.agentclientauthorisation

import org.joda.time.DateTime
import org.scalatest.{Inside, Inspectors}
import uk.gov.hmrc.agentclientauthorisation.model.AuthorisationRequest
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, Resource}
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with AppAndStubs with Inspectors with Inside {

  private implicit val me = AgentCode("ABCDEF12345678")

  // TODO shouldn't auth stuff be written as a behaviour?
  "GET /request/:agentCode" should {
    "return 401 when the requester is not authenticated" in {
      given().agentAdmin(me).isNotLoggedIn()
      responseForGetRequests.status shouldBe 401
    }

    "return 401 when user is not an agent" ignore {
      ??? // do we need this?
    }

    "return 401 when user is an agent, but not subscribed to SA" ignore {
      ??? // do we need this?
    }
  }

  "POST /request" should {
    "return 401 when the requester is not authenticated" in {
      given().agentAdmin(me).isNotLoggedIn()
      responseForCreateRequest("").status shouldBe 401
    }
  }

  "/request" should {
    "create and retrieve authorisation requests" in {
      given().agentAdmin(me).isLoggedIn()

      val now = DateTime.now().getMillis
      val beRecent = be <= now and be >= (now - 10)

      note("there should be no requests")
      inside (responseForGetRequests) { case resp =>
        resp.status shouldBe 200
        resp.json.as[Set[AuthorisationRequest]] shouldBe 'empty
      }

      note("we should be able to add 2 new requests")
      responseForCreateRequest("""{"agentCode": "ABCDEF123456", "clientSaUtr": "1234567890"}""").status shouldBe 201
      responseForCreateRequest("""{"agentCode": "ABCDEF123456", "clientSaUtr": "1234567891"}""").status shouldBe 201

      note("the freshly added authorisation requests should be available")
      val requests = responseForGetRequests.json.as[Set[AuthorisationRequest]]
      requests should have size 2
      forAll (requests) { request =>
        inside (request) { case AuthorisationRequest(_, me, SaUtr("1234567890") | SaUtr("1234567891"), requestDate) =>
          requestDate.getMillis should beRecent
        }
      }
    }
  }

  def responseForGetRequests(implicit agentCode: AgentCode): HttpResponse = {
    new Resource(s"/agent-client-authorisation/sa/agent/$agentCode/requests", port).get()
  }

  def responseForCreateRequest(body: String)(implicit agentCode: AgentCode): HttpResponse =
    new Resource(s"/agent-client-authorisation/sa/agent/$agentCode/requests", port).postAsJson(body)

}
