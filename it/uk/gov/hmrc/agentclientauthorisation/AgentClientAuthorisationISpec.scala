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
import play.api.libs.json.{JsArray, JsString, JsValue}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with UserDetailsStub with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val agentCode = AgentCode("ABCDEF12345678")

  private val getRequestsUrl = "/agent-client-authorisation/requests"
  private val createRequestUrl = "/agent-client-authorisation/requests"

  "GET /requests" should {
    behave like anEndpointAccessibleForSaAgentsOrSaClients(responseForGetRequests())
  }

  "POST /requests" should {
    val clientRegimeId = SaUtr("1234567899")
    behave like anEndpointAccessibleForSaAgentsOnly(responseForCreateRequest(s"""{"agentCode": "${agentCode.value}", "clientRegimeId": "$clientRegimeId", "clientPostcode": "AA1 1AA"}"""))
  }

  "/requests" should {
    "create and retrieve authorisation requests" in {
      val testStartTime = DateTime.now().getMillis
      val beRecent = be >= testStartTime and be <= (testStartTime + 5000)

      val (client1SaUtr: SaUtr, client2SaUtr: SaUtr) = createRequests

      note("the freshly added authorisation requests should be available")
      val (responseJson, requestsArray) = eventually { // MongoDB is slow sometimes
        val responseJson = responseForGetRequests().json

        Logger.info(s"responseJson = $responseJson")

        val requestsArray = requests(responseJson).value.sortBy(j => (j \ "clientRegimeId").as[String])
        requestsArray should have size 2
        (responseJson, requestsArray)
      }

      val selfLinkHref = (responseJson \ "_links" \ "self" \ "href").as[String]
      selfLinkHref shouldBe getRequestsUrl

      val firstRequest = requestsArray head
      val secondRequest = requestsArray(1)

      val alphanumeric = "[0-9A-Za-z]+"

      val firstRequestId = (firstRequest \ "id").as[String]
      firstRequestId should fullyMatch regex alphanumeric
      (firstRequest \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/requests/$firstRequestId"
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

      val secondRequestId = (secondRequest \ "id").as[String]
      secondRequestId should fullyMatch regex alphanumeric
      (secondRequest \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/requests/$secondRequestId"
      (secondRequest \ "agentCode") shouldBe JsString(agentCode.value)
      (secondRequest \ "regime") shouldBe JsString("sa")
      (secondRequest \ "clientRegimeId") shouldBe JsString(client2SaUtr.utr)
      (secondRequest \ "clientFullName") shouldBe JsString("Mrs First Last")
      (secondRequest \ "agentName") shouldBe JsString("Agent Name")
      (secondRequest \ "agentFriendlyName") shouldBe JsString("DDCW Accountancy Ltd")
      (secondRequest \ "regime") shouldBe JsString("sa")
      ((secondRequest \ "events")(0) \ "time").as[Long] should beRecent
      ((secondRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
      (secondRequest \ "events").as[JsArray].value should have size 1

      firstRequestId should not be secondRequestId
    }
  }


  def createRequests: (SaUtr, SaUtr) = {
    dropMongoDb()
    val agent = given().agentAdmin(agentCode).isLoggedIn().andHasIrSaAgentEnrolment()
    userExists(agent)
    val client1SaUtr = SaUtr("1234567890")
    val client2SaUtr = SaUtr("1234567891")
    CesaStubs.saTaxpayerExists(client1SaUtr)
    CesaStubs.saTaxpayerExists(client2SaUtr, "Mrs")


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
    (client1SaUtr, client2SaUtr)
  }

  "POST /requests/:id/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptRequest("request-id"))
  }

  "GET /requests/:id" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForGetRequest("request-id"))
  }

  def requests(response: JsValue) = {
    (response \ "_embedded" \ "requests").as[JsArray]
  }

  def requestId(response: JsValue, saUtr: SaUtr): String =  {
    val req = requests(response).value.filter(v => (v \ "clientRegimeId").as[String] == saUtr.utr).head
    (req \ "id").as[String]
  }

  "/requests/:id/accept" should {
    "mark the request as accepted" in {
      val (client1SaUtr, _) = createRequests
      val responseJson = responseForGetRequests().json

      given().client().isLoggedIn(client1SaUtr.utr)

      val id: String = requestId(responseJson, client1SaUtr)
      val accept = responseForAcceptRequest(id)
      accept.status shouldBe 200

      val authRequestJson = responseForGetRequest(id).json

      (authRequestJson \ "events")(1) \ "status" shouldBe JsString("Accepted")
    }

    behave like aClientStatusChange(responseForAcceptRequest)
  }

  "/requests/:id/reject" should {
    "mark the request as rejected" in {
      val (client1SaUtr, _) = createRequests
      val responseJson = responseForGetRequests().json

      given().client().isLoggedIn(client1SaUtr.utr)

      val id: String = requestId(responseJson, client1SaUtr)
      responseForRejectRequest(id).status shouldBe 200

      val authRequestJson = responseForGetRequest(id).json

      (authRequestJson \ "events")(1) \ "status" shouldBe JsString("Rejected")
    }

    behave like aClientStatusChange(responseForRejectRequest)
  }

  def responseForAcceptRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests/$requestId/accept", port).postEmpty()
  }

  def responseForRejectRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests/$requestId/reject", port).postEmpty()
  }

  def responseForGetRequest(requestId: String ): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests/$requestId", port).get()
  }

  def responseForGetRequests(): HttpResponse = {
    new Resource(getRequestsUrl, port).get()
  }

  def responseForCreateRequest(body: String): HttpResponse =
    new Resource(createRequestUrl, port).postAsJson(body)


  def aClientStatusChange(doStatusChangeRequest: String => HttpResponse) = {
    "return not found for an unknown request" in {
      doStatusChangeRequest("some-request-id").status shouldBe 404
    }

    "return forbidden for a request for a different user" in {
      val (client1SaUtr, client2SaUtr) = createRequests
      val responseJson = responseForGetRequests().json

      given().client().isLoggedIn(client1SaUtr.utr)

      val id: String = requestId(responseJson, client2SaUtr)
      doStatusChangeRequest(id).status shouldBe 403
    }

    "return forbidden for a request not in Pending status" in {
      val (client1SaUtr, _) = createRequests
      val responseJson = responseForGetRequests().json

      given().client().isLoggedIn(client1SaUtr.utr)

      val id: String = requestId(responseJson, client1SaUtr)
      doStatusChangeRequest(id).status shouldBe 200
      eventually {
        doStatusChangeRequest(id).status shouldBe 403
      }
    }
  }
}
