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
import org.skyscreamer.jsonassert.{JSONCompareMode, JSONCompare}
import play.api.libs.json.{JsString, JsArray}
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationRequest, EnrichedAgentClientAuthorisationRequest, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val agentCode = AgentCode("ABCDEF12345678")

  "GET /requests" should {
    behave like anEndpointAccessibleForAgentsOnly(responseForGetRequests)
  }

  "POST /requests" should {
    val clientSaUtr = SaUtr("1234567899")
    behave like anEndpointAccessibleForAgentsOnly(responseForCreateRequest(s"""{"agentCode": "${agentCode.value}", "clientSaUtr": "$clientSaUtr", "clientPostcode": "AA1 1AA"}"""))
  }

  "/requests" should {
    "create and retrieve authorisation requests" in {
      dropMongoDb()
      given().agentAdmin(agentCode).isLoggedIn()
      val client1SaUtr = SaUtr("1234567890")
      val client2SaUtr = SaUtr("1234567891")
      CesaStubs.saTaxpayerExists(client1SaUtr)
      CesaStubs.saTaxpayerExists(client2SaUtr, "Mrs")

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
      responseForCreateRequest(s"""{"agentCode": "$agentCode", "clientSaUtr": "$client1SaUtr", "clientPostcode": "AA1 1AA"}""").status shouldBe 201
      responseForCreateRequest(s"""{"agentCode": "$agentCode", "clientSaUtr": "$client2SaUtr", "clientPostcode": "AA1 1AA"}""").status shouldBe 201

      note("the freshly added authorisation requests should be available")
      eventually { // MongoDB is slow sometimes
        val requestsArray = (responseForGetRequests.json \ "_embedded" \ "requests").as[JsArray]
        val requests = requestsArray.value.sortBy(j => (j \ "clientSaUtr").as[String])
        requests should have size 2

        val firstRequest = requests head
        val secondRequest = requests(1)

        (firstRequest \ "agentCode") shouldBe JsString(agentCode.value)
        (firstRequest \ "clientSaUtr") shouldBe JsString(client1SaUtr.utr) // TODO consider renaming this to clientRegimeId
        (firstRequest \ "clientFullName") shouldBe JsString("Mr First Last")
        (firstRequest \ "regime") shouldBe JsString("sa")
        ((firstRequest \ "events")(0) \ "time").as[Long] should beRecent
        ((firstRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
        (firstRequest \ "events").as[JsArray].value should have size(1)

        (secondRequest \ "agentCode") shouldBe JsString(agentCode.value)
        (secondRequest \ "clientSaUtr") shouldBe JsString(client2SaUtr.utr)
        (secondRequest \ "clientFullName") shouldBe JsString("Mrs First Last")
        (secondRequest \ "regime") shouldBe JsString("sa")
        ((secondRequest \ "events")(0) \ "time").as[Long] should beRecent
        ((secondRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
        (secondRequest \ "events").as[JsArray].value should have size(1)
      }
    }
  }

  def responseForGetRequests(): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests", port).get()
  }

  def responseForCreateRequest(body: String): HttpResponse =
    new Resource(s"/agent-client-authorisation/requests", port).postAsJson(body)

}
