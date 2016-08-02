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
import play.api.Logger
import play.api.libs.json.{JsArray, JsString}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with UserDetailsStub with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val agentCode = AgentCode("ABCDEF12345678")

  private val getRequestsUrl = "/agent-client-authorisation/requests"
  private val createRequestUrl = "/agent-client-authorisation/requests"

  "GET /requests" should {
    behave like anEndpointAccessibleForSaAgentsOnly(responseForGetRequests())
  }

  "POST /requests" should {
    val clientRegimeId = SaUtr("1234567899")
    behave like anEndpointAccessibleForSaAgentsOnly(responseForCreateRequest(s"""{"agentCode": "${agentCode.value}", "clientRegimeId": "$clientRegimeId", "clientPostcode": "AA1 1AA"}"""))
  }

  "/requests" should {
    "create and retrieve authorisation requests" in {
      dropMongoDb()
      val agent = given().agentAdmin(agentCode).isLoggedIn().andHasIrSaAgentEnrolment()
      userExists(agent)
      val client1SaUtr = SaUtr("1234567890")
      val client2SaUtr = SaUtr("1234567891")
      CesaStubs.saTaxpayerExists(client1SaUtr)
      CesaStubs.saTaxpayerExists(client2SaUtr, "Mrs")

      val testStartTime = DateTime.now().getMillis
      val beRecent = be >= testStartTime and be <= (testStartTime + 5000)

      note("there should be no requests")
      eventually {
        inside(responseForGetRequests()) { case resp =>
          resp.status shouldBe 200
          (resp.json \ "_embedded" \ "requests").as[JsArray].value shouldBe 'empty
        }
      }

      note("we should be able to add 2 new requests")
      responseForCreateRequest(s"""{"agentCode": "$agentCode", "regime": "sa", "clientRegimeId": "$client1SaUtr", "clientPostcode": "AA1 1AA"}""").status shouldBe 201
      responseForCreateRequest(s"""{"agentCode": "$agentCode", "regime": "sa", "clientRegimeId": "$client2SaUtr", "clientPostcode": "AA1 1AA"}""").status shouldBe 201

      note("the freshly added authorisation requests should be available")
      eventually { // MongoDB is slow sometimes
        val responseJson = responseForGetRequests().json

        Logger.info(s"responseJson = $responseJson")
        val selfLinkHref = (responseJson \ "_links" \ "self" \ "href").as[String]
        selfLinkHref shouldBe getRequestsUrl

        val requestsArray = (responseJson \ "_embedded" \ "requests").as[JsArray]
        val requests = requestsArray.value.sortBy(j => (j \ "clientRegimeId").as[String])
        requests should have size 2

        val firstRequest = requests head
        val secondRequest = requests(1)

        (firstRequest \ "agentCode") shouldBe JsString(agentCode.value)
        (firstRequest \ "regime") shouldBe JsString("sa")
        (firstRequest \ "clientRegimeId") shouldBe JsString(client1SaUtr.utr)
        (firstRequest \ "clientFullName") shouldBe JsString("Mr First Last")
        (firstRequest \ "agentName") shouldBe JsString("Agent Name")
        (firstRequest \ "agentFriendlyName") shouldBe JsString("DDCW Accountancy Ltd")
        (firstRequest \ "regime") shouldBe JsString("sa")
        ((firstRequest \ "events")(0) \ "time").as[Long] should beRecent
        ((firstRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
        (firstRequest \ "events").as[JsArray].value should have size 1

        (secondRequest \ "agentCode") shouldBe JsString(agentCode.value)
        (secondRequest \ "regime") shouldBe JsString("sa")
        (secondRequest \ "clientRegimeId") shouldBe JsString(client2SaUtr.utr)
        (secondRequest \ "clientFullName") shouldBe JsString("Mrs First Last")
        (firstRequest \ "agentName") shouldBe JsString("Agent Name")
        (firstRequest \ "agentFriendlyName") shouldBe JsString("DDCW Accountancy Ltd")
        (secondRequest \ "regime") shouldBe JsString("sa")
        ((secondRequest \ "events")(0) \ "time").as[Long] should beRecent
        ((secondRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
        (secondRequest \ "events").as[JsArray].value should have size 1
      }
    }
  }


  "/requests/:id/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptRequest("request-id"))
  }

  def responseForAcceptRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests/$requestId/accept", port).postEmpty()
  }

  def responseForGetRequests(): HttpResponse = {
    new Resource(getRequestsUrl, port).get()
  }

  def responseForCreateRequest(body: String): HttpResponse =
    new Resource(createRequestUrl, port).postAsJson(body)

}
