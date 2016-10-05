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
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val arn = Arn("ABCDEF12345678")

  private val getRequestsUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
  private val createRequestUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"

  "GET /requests" should {
    behave like anEndpointAccessibleForSaAgentsOrSaClients(responseForGetRequests())
  }

  "POST /requests" should {
    val customerRegimeId = SaUtr("1234567899")
    behave like anEndpointAccessibleForSaAgentsOnly(responseForCreateRequest(s"""{"arn": "${arn.value}", "customerRegimeId": "$customerRegimeId", "postcode": "AA1 1AA"}"""))
  }

  "/requests" should {
    "create and retrieve authorisation requests" in {
      val testStartTime = DateTime.now().getMillis
      val beRecent = be >= testStartTime and be <= (testStartTime + 5000)

      val (customer1SaUtr: SaUtr, customer2SaUtr: SaUtr) = createRequests

      note("the freshly added authorisation requests should be available")
      val (responseJson, requestsArray) = eventually { // MongoDB is slow sometimes
        val responseJson = responseForGetRequests().json

        Logger.info(s"responseJson = $responseJson")

        val requestsArray = requests(responseJson).value.sortBy(j => (j \ "customerRegimeId").as[String])
        requestsArray should have size 2
        (responseJson, requestsArray)
      }

//      val selfLinkHref = (responseJson \ "_links" \ "self" \ "href").as[String]
//      selfLinkHref shouldBe getRequestsUrl

      val firstRequest = requestsArray head
      val secondRequest = requestsArray(1)

      val alphanumeric = "[0-9A-Za-z]+"

      val firstRequestId = (firstRequest \ "id" \ "$oid").as[String]
      firstRequestId should fullyMatch regex alphanumeric
//      (firstRequest \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/requests/$firstRequestId"
      (firstRequest \ "arn") shouldBe JsString(arn.arn)
      (firstRequest \ "regime") shouldBe JsString("sa")
      (firstRequest \ "customerRegimeId") shouldBe JsString(customer1SaUtr.utr)
      (firstRequest \ "regime") shouldBe JsString("sa")
      ((firstRequest \ "events")(0) \ "time").as[Long] should beRecent
      ((firstRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
      (firstRequest \ "events").as[JsArray].value should have size 1

      val secondRequestId = (secondRequest \ "id" \ "$oid").as[String]
      secondRequestId should fullyMatch regex alphanumeric
//      (secondRequest \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/requests/$secondRequestId"
      (secondRequest \ "arn") shouldBe JsString(arn.arn)
      (secondRequest \ "regime") shouldBe JsString("sa")
      (secondRequest \ "customerRegimeId") shouldBe JsString(customer2SaUtr.utr)
      (secondRequest \ "regime") shouldBe JsString("sa")
      ((secondRequest \ "events")(0) \ "time").as[Long] should beRecent
      ((secondRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
      (secondRequest \ "events").as[JsArray].value should have size 1

      firstRequestId should not be secondRequestId
    }
  }


  def createRequests: (SaUtr, SaUtr) = {
    dropMongoDb()
    val agent = given().agentAdmin(arn).isLoggedIn().andHasIrSaAgentEnrolment()
    val customer1SaUtr = SaUtr("1234567890")
    val customer2SaUtr = SaUtr("1234567891")


    note("there should be no requests")
    eventually {
      inside(responseForGetRequests()) { case resp =>
        resp.status shouldBe 200
        requests(resp.json).value shouldBe 'empty
      }
    }

    note("we should be able to add 2 new requests")
    responseForCreateRequest(s"""{"arn": "${arn.arn}", "regime": "sa", "customerRegimeId": "$customer1SaUtr", "postcode": "AA1 1AA"}""").status shouldBe 201
    responseForCreateRequest(s"""{"arn": "${arn.arn}", "regime": "sa", "customerRegimeId": "$customer2SaUtr", "postcode": "AA1 1AA"}""").status shouldBe 201
    (customer1SaUtr, customer2SaUtr)
  }

  "PUT /requests/:id/accept" is {
    pending
//    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptRequest("request-id"))
  }

  "GET /requests/:id" is {
    pending
//    behave like anEndpointAccessibleForSaClientsOnly(responseForGetRequest("request-id"))
  }

  def requests(response: JsValue) = {
//    (response \ "_embedded" \ "invitations").as[JsArray]
    response.as[JsArray]
  }

  def requestId(response: JsValue, saUtr: SaUtr): String =  {
    val req = requests(response).value.filter(v => (v \ "customerRegimeId").as[String] == saUtr.utr).head
    (req \ "id").as[String]
  }

  def responseForAcceptRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-customer-authorisation/requests/$requestId/accept", port).postEmpty()
  }

  def responseForRejectRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-customer-authorisation/requests/$requestId/reject", port).postEmpty()
  }

  def responseForGetRequest(requestId: String ): HttpResponse = {
    new Resource(s"/agent-customer-authorisation/requests/$requestId", port).get()
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
      val (customer1SaUtr, customer2SaUtr) = createRequests
      val responseJson = responseForGetRequests().json

      given().customer().isLoggedIn(customer1SaUtr.utr)

      val id: String = requestId(responseJson, customer2SaUtr)
      doStatusChangeRequest(id).status shouldBe 403
    }

    "return forbidden for a request not in Pending status" in {
      val (customer1SaUtr, _) = createRequests
      val responseJson = responseForGetRequests().json

      given().customer().isLoggedIn(customer1SaUtr.utr)

      val id: String = requestId(responseJson, customer1SaUtr)
      doStatusChangeRequest(id).status shouldBe 200
      eventually {
        doStatusChangeRequest(id).status shouldBe 403
      }
    }
  }
}
