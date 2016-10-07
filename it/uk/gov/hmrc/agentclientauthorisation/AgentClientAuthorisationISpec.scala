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
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")

  private val REGIME: String = "mtd-sa"
  private val getRequestsUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
  private val createRequestUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"

  "GET /requests" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForGetRequests())
  }

  "POST /requests" should {
    val customerRegimeId = "1234567899"
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "$customerRegimeId", "postcode": "AA1 1AA"}"""))
  }

  "/agencies/:arn/invitations" should {
    "create and retrieve invitations" in {
      val testStartTime = DateTime.now().getMillis
      val beRecent = be >= testStartTime and be <= (testStartTime + 5000)

      val (customer1Id: String, customer2Id: String) = createRequests

      note("the freshly added invitations should be available")
      val (responseJson, requestsArray) = eventually { // MongoDB is slow sometimes
        val responseJson = responseForGetRequests().json

        Logger.info(s"responseJson = $responseJson")

        val requestsArray = requests(responseJson).value.sortBy(j => (j \ "customerRegimeId").as[String])
        requestsArray should have size 2
        (responseJson, requestsArray)
      }

      val selfLinkHref = (responseJson \ "_links" \ "self" \ "href").as[String]
      selfLinkHref shouldBe getRequestsUrl

      val firstRequest = requestsArray head
      val secondRequest = requestsArray(1)

      val alphanumeric = "[0-9A-Za-z]+"

      val firstRequestId = (firstRequest \ "id" \ "$oid").as[String]
      firstRequestId should fullyMatch regex alphanumeric
      (firstRequest \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/$firstRequestId"
      (firstRequest \ "arn") shouldBe JsString(arn.arn)
      (firstRequest \ "regime") shouldBe JsString(REGIME)
      (firstRequest \ "customerRegimeId") shouldBe JsString(customer1Id)
      (firstRequest \ "regime") shouldBe JsString(REGIME)
      ((firstRequest \ "events")(0) \ "time").as[Long] should beRecent
      ((firstRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
      (firstRequest \ "events").as[JsArray].value should have size 1

      val secondRequestId = (secondRequest \ "id" \ "$oid").as[String]
      secondRequestId should fullyMatch regex alphanumeric
      (secondRequest \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/$secondRequestId"
      (secondRequest \ "arn") shouldBe JsString(arn.arn)
      (secondRequest \ "regime") shouldBe JsString(REGIME)
      (secondRequest \ "customerRegimeId") shouldBe JsString(customer2Id)
      (secondRequest \ "regime") shouldBe JsString(REGIME)
      ((secondRequest \ "events")(0) \ "time").as[Long] should beRecent
      ((secondRequest \ "events")(0) \ "status") shouldBe JsString("Pending")
      (secondRequest \ "events").as[JsArray].value should have size 1

      firstRequestId should not be secondRequestId
    }

    "should not create invitation if postcodes do not match" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "9876543210", "postcode": "BA1 1AA"}""").status shouldBe 403
    }

    "should not create invitation if postcode is not in a valid format" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "9876543210", "postcode": "BAn 1AA"}""").status shouldBe 400
    }

    "should not create invitation for an unsupported regime" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "sa", "customerRegimeId": "9876543210", "postcode": "A11 1AA"}""")
      response.status shouldBe 501
      (response.json \ "code").as[String] shouldBe "UNSUPPORTED_REGIME"
      (response.json \ "message").as[String] shouldBe "Unsupported regime \"sa\", the only currently supported regime is \"mtd-sa\""
    }

    "should create invitation if postcode has no spaces" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "9876543210", "postcode": "AA11AA"}""").status shouldBe 201
    }

    "should create invitation if postcode has more than one space" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "9876543210", "postcode": "A A1 1A A"}""").status shouldBe 201
    }
  }


  def createRequests: (String, String) = {
    dropMongoDb()
    val agent = given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
    val customer1Id = "1234567890"
    val customer2Id = "1234567891"


    note("there should be no requests")
    eventually {
      inside(responseForGetRequests()) { case resp =>
        resp.status shouldBe 200
        requests(resp.json).value shouldBe 'empty
      }
    }

    note("we should be able to add 2 new requests")
    checkCreatedResponse(responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "$customer1Id", "postcode": "AA1 1AA"}"""))
    checkCreatedResponse(responseForCreateInvitation(s"""{"arn": "${arn.arn}", "regime": "$REGIME", "customerRegimeId": "$customer2Id", "postcode": "AA1 1AA"}"""))
    (customer1Id, customer2Id)
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
    (response \ "_embedded" \ "invitations").as[JsArray]
  }

  def requestId(response: JsValue, customerId: String): String =  {
    val req = requests(response).value.filter(v => (v \ "customerRegimeId").as[String] == customerId).head
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

  def responseForCreateInvitation(body: String): HttpResponse =
    new Resource(createRequestUrl, port).postAsJson(body)

  def checkCreatedResponse(httpResponse: HttpResponse) = {
    httpResponse.status shouldBe 201
    httpResponse.header("location").get should startWith (s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/")
  }

  def aClientStatusChange(doStatusChangeRequest: String => HttpResponse) = {
    "return not found for an unknown request" in {
      doStatusChangeRequest("some-request-id").status shouldBe 404
    }

    "return forbidden for a request for a different user" in {
      val (customer1Id, customer2Id) = createRequests
      val responseJson = responseForGetRequests().json

      given().customer().isLoggedIn(customer1Id)

      val id: String = requestId(responseJson, customer2Id)
      doStatusChangeRequest(id).status shouldBe 403
    }

    "return forbidden for a request not in Pending status" in {
      val (customer1Id, _) = createRequests
      val responseJson = responseForGetRequests().json

      given().customer().isLoggedIn(customer1Id)

      val id: String = requestId(responseJson, customer1Id)
      doStatusChangeRequest(id).status shouldBe 200
      eventually {
        doStatusChangeRequest(id).status shouldBe 403
      }
    }
  }
}
