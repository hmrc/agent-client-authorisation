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
import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, Inspectors}
import play.api.libs.json.JsArray
import uk.gov.hmrc.agentclientauthorisation.model.{Pending, StatusChangeEvent, AgentClientAuthorisationRequest}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val me = AgentCode("ABCDEF12345678")
  private implicit val saUtr = SaUtr("1234567890")

  "GET /request/:agentCode" should {
    behave like anEndpointAccessibleForAgentsOnly(responseForGetRequests)
  }

  "POST /request" should {
    behave like anEndpointAccessibleForAgentsOnly(responseForCreateRequest("""{"clientSaUtr": "1234567899"}"""))
  }

  "/request" should {
    "create and retrieve authorisation requests" in {
      dropMongoDb()
      given().agentAdmin(me).isLoggedIn()

      val testStartTime = DateTime.now().getMillis
      val beRecent = be >= testStartTime and be <= (testStartTime + 5000)

      note("there should be no requests")
      eventually {
        inside(responseForGetRequests) { case resp =>
          resp.status shouldBe 200
          (resp.json \ "_embedded" \ "requests").as[JsArray].value shouldBe 'empty
        }
      }

      note("we should be able to add 2 new requests")
      responseForCreateRequest("""{"clientSaUtr": "1234567890"}""").status shouldBe 201
      responseForCreateRequest("""{"clientSaUtr": "1234567891"}""").status shouldBe 201

      note("the freshly added authorisation requests should be available")
      eventually { // MongoDB is slow sometimes
        val requests = (responseForGetRequests.json \ "_embedded" \ "requests").as[Set[AgentClientAuthorisationRequest]]
        requests should have size 2
        forAll (requests) { request =>
          inside (request) { case AgentClientAuthorisationRequest(_, me, SaUtr("1234567890") | SaUtr("1234567891"), "sa", List(StatusChangeEvent(requestDate, Pending))) =>
            requestDate.getMillis should beRecent
          }
        }
      }
    }
  }
  "/sa/:saUtr/requests" should {
    "retrieve authorisation requests" in {
      dropMongoDb()

      val testStartTime = DateTime.now().getMillis
      val beRecent = be >= testStartTime and be <= (testStartTime + 5000)


      note("there should be no requests")
      given().client().isLoggedIn()
      eventually {
        inside(responseForClientGetRequests) { case resp =>
          resp.status shouldBe 200
          resp.json.as[JsArray].value shouldBe 'empty
        }
      }

      note("we should be able to add 2 new requests")
      given().agentAdmin(me).isLoggedIn()
      responseForCreateRequest("""{"clientSaUtr": "1234567890"}""").status shouldBe 201
      responseForCreateRequest("""{"clientSaUtr": "1234567891"}""").status shouldBe 201

      given().client().isLoggedIn()
      note("the freshly added authorisation requests should be available")
      eventually {
        val requests = responseForClientGetRequests.json.as[Set[AgentClientAuthorisationRequest]]
        requests should have size 1
        forAll (requests) { request =>
          inside (request) { case AgentClientAuthorisationRequest(_, me, SaUtr("1234567890"), "sa", List(StatusChangeEvent(requestDate, Pending))) =>
            requestDate.getMillis should beRecent
          }
        }
      }
    }
  }

  def responseForGetRequests(implicit agentCode: AgentCode): HttpResponse = {
    new Resource(s"/agent-client-authorisation/agent/$agentCode/requests", port).get()
  }

  def responseForClientGetRequests(implicit saUtr: SaUtr): HttpResponse = {
    new Resource(s"/agent-client-authorisation/sa/$saUtr/requests", port).get()
  }

  def responseForCreateRequest(body: String)(implicit agentCode: AgentCode): HttpResponse =
    new Resource(s"/agent-client-authorisation/agent/$agentCode/requests", port).postAsJson(body)

}
