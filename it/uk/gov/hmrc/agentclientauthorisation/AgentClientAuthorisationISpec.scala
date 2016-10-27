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
import play.api.libs.json._
import play.mvc.Http.HeaderNames.LOCATION
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class AgentClientAuthorisationISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with SecuredEndpointBehaviours {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")

  private val REGIME: String = "mtd-sa"
  private val getInvitationsUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
  private val getInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/"
  private val createInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"

  val clientId: String = "1234567890"
  private val getClientInvitationUrl = s"/agent-client-authorisation/clients/$clientId/invitations/received/"
  private val getClientInvitationsUrl = s"/agent-client-authorisation/clients/$clientId/invitations/received"


  "GET /clients/:clientId/invitations/received" should {
    "return a 200 response" in {
      val ((_, client1Id), _) = createInvitations

      given().client(client1Id).isLoggedIn(client1Id)

      val response = new Resource(s"/agent-client-authorisation/clients/$client1Id/invitations/received", port).get
      response.status shouldBe 200

      val json: JsValue = response.json
      val invitation = invitations(json)
      invitation.value.size should be >= 1
    }

    "return a 401 response if not logged-in" in {

      given().client(clientId).isNotLoggedIn()
      responseForGetClientInvitations().status shouldBe 401
    }

    "return 404 when try to access someone else's invitations" in {
      val ((_, client1Id), (_,client2Id)) = createInvitations

      given().client().isLoggedIn(client1Id.value.toString)

      val response = new Resource(s"/agent-client-authorisation/clients/$client2Id/invitations/received", port).get

      response.status shouldBe 403
    }
  }

  "GET /clients/:clientId/invitations/received/:invitation" should {
    "return a 200 response" in {

      val testStartTime = DateTime.now.getMillis
      val ((invitation1Id, client1Id), _) = createInvitations

      given().client(client1Id).isLoggedIn(client1Id)

      val response = new Resource(s"/agent-client-authorisation/clients/$client1Id/invitations/received/$invitation1Id", port).get
      response.status shouldBe 200

      val invitation = response.json
      checkClientsInvitation(client1Id, invitation, testStartTime)
    }

    "return a 401 response if not logged-in" in {
      val ((invitation1Id, _), _) = createInvitations

      given().client(clientId).isNotLoggedIn()
      responseForGetClientInvitation(invitation1Id).status shouldBe 401
    }

    "return 404 when invitation not found" in {

      val response = responseForGetClientInvitation("none")
      response.status shouldBe 404
    }

    "return 403 when try to access someone else's invitation" in {
      val ((_, client1Id), (invitation2Id,client2Id)) = createInvitations

      given().client().isLoggedIn(client1Id)
      val response = new Resource(s"/agent-client-authorisation/clients/$client2Id/invitations/received/$invitation2Id", port).get
      response.status shouldBe 403
    }
  }

  "GET /agencies/:arn/invitations/sent" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForGetInvitations())

    "return 403 for someone else's invitation list" in {
      given().agentAdmin(Arn("98765"), AgentCode("123456")).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = responseForGetInvitations()

      response.status shouldBe 403
    }

    "return only invitations for the specified client" in {
      val clientRegimeId = "1234567890"
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$clientRegimeId", "postcode": "AA1 1AA"}""").header("location")
      responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "9876543210", "postcode": "AA1 1AA"}""").header("location")

      val response = responseForGetInvitations(clientRegimeId)

      response.status shouldBe 200
      val invitation = invitations(response.json)
      invitation.value.size shouldBe 1
      invitation.value.head \ "clientRegimeId" shouldBe JsString(clientRegimeId)
      response.json \ "_links" \ "self" \ "href" shouldBe JsString("/agent-client-authorisation/agencies/ABCDEF12345678/invitations/sent?clientRegimeId=1234567890")
    }
  }

  "POST /agencies/:arn/invitations" should {
    val clientRegimeId = "1234567899"
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$clientRegimeId", "postcode": "AA1 1AA"}"""))
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {
    behave like anEndpointAccessibleForMtdAgentsOnly(responseForGetInvitation())

    "Return a created invitation" in {
      val testStartTime = DateTime.now().getMillis
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val clientRegimeId = "1234567899"
      val location = responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$clientRegimeId", "postcode": "AA1 1AA"}""").header("location")

      val invitation = new Resource(location.get, port).get().json
      checkInvitation(clientRegimeId, invitation, testStartTime)
    }

    "Return 404 for an invitation that doesn't exist" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()

      val response = responseForGetInvitation(BSONObjectID.generate.stringify)
      response.status shouldBe 404
    }

    "Return 403 if accessing someone else's invitation" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val clientRegimeId = "1234567899"
      val location = responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$clientRegimeId", "postcode": "AA1 1AA"}""").header("location")

      given().agentAdmin(Arn("98765"), AgentCode("123456")).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = new Resource(location.get, port).get()
      response.status shouldBe 403
    }
  }

  "/agencies/:arn/invitations" should {
    "create and retrieve invitations" in {
      val testStartTime = DateTime.now().getMillis
      val ((_, client1Id), (invitation2Id,client2Id)) = createInvitations

      note("the freshly added invitations should be available")
      val (responseJson, invitationsArray) = eventually {
        // MongoDB is slow sometimes
        val responseJson = responseForGetInvitations().json

        Logger.info(s"responseJson = $responseJson")

        val requestsArray = invitations(responseJson).value.sortBy(j => (j \ "clientRegimeId").as[String])
        requestsArray should have size 2
        (responseJson, requestsArray)
      }

      val selfLinkHref = (responseJson \ "_links" \ "self" \ "href").as[String]
      selfLinkHref shouldBe getInvitationsUrl

      val firstInvitation = invitationsArray head
      val secondInvitation = invitationsArray(1)

      val firstInvitationId = checkInvitation(client1Id, firstInvitation, testStartTime)
      val secondInvitationId = checkInvitation(client2Id, secondInvitation, testStartTime)

      firstInvitationId should not be secondInvitationId
    }

    "create and retrieve duplicate invitations" in {
      val testStartTime = DateTime.now().getMillis
      val ((_, "1234567890"), (invitation2Id,"1234567890")) = createDuplicateInvitations

      note("the freshly added invitations should be available")
      val (responseJson, invitationsArray) = eventually {
        // MongoDB is slow sometimes
        val responseJson = responseForGetInvitations().json

        Logger.info(s"responseJson = $responseJson")

        val requestsArray = invitations(responseJson).value.sortBy(j => (j \ "clientRegimeId").as[String])
        requestsArray should have size 2
        (responseJson, requestsArray)
      }

      val selfLinkHref = (responseJson \ "_links" \ "self" \ "href").as[String]
      selfLinkHref shouldBe getInvitationsUrl

      val firstInvitation = invitationsArray.head
      val secondInvitation = invitationsArray(1)

      val firstInvitationId = checkInvitation("1234567890", firstInvitation, testStartTime)
      val secondInvitationId = checkInvitation("1234567890", secondInvitation, testStartTime)

      firstInvitationId should not be secondInvitationId
    }

    "should not create invitation if postcodes do not match" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "9876543210", "postcode": "BA1 1AA"}""").status shouldBe 403
    }

    "should not create invitation if postcode is not in a valid format" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "9876543210", "postcode": "BAn 1AA"}""").status shouldBe 400
    }

    "should not create invitation for an unsupported regime" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      val response = responseForCreateInvitation(s"""{"regime": "sa", "clientRegimeId": "9876543210", "postcode": "A11 1AA"}""")
      response.status shouldBe 501
      (response.json \ "code").as[String] shouldBe "UNSUPPORTED_REGIME"
      (response.json \ "message").as[String] shouldBe "Unsupported regime \"sa\", the only currently supported regime is \"mtd-sa\""
    }

    "should create invitation if postcode has no spaces" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "9876543210", "postcode": "AA11AA"}""").status shouldBe 201
    }

    "should create invitation if postcode has more than one space" in {
      given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
      responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "9876543210", "postcode": "A A1 1A A"}""").status shouldBe 201
    }
  }


  def checkInvitation(client2Id: String, invitation: JsValue, testStartTime: Long): String = {
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val alphanumeric = "[0-9A-Za-z]+"
    val invitationId = (invitation \ "id").as[String]
    invitationId should fullyMatch regex alphanumeric
    (invitation \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/$invitationId"
    (invitation \ "_links" \ "cancel" \ "href").as[String] shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/$invitationId"
    (invitation \ "_links" \ "agency" \ "href").as[String] shouldBe s"http://localhost:$wiremockPort/agencies-fake/agencies/${arn.arn}"
    (invitation \ "arn") shouldBe JsString(arn.arn)
    (invitation \ "regime") shouldBe JsString(REGIME)
    (invitation \ "clientRegimeId") shouldBe JsString(client2Id)
    (invitation \ "status") shouldBe JsString("Pending")
    (invitation \ "created").as[Long] should beRecent
    (invitation \ "lastUpdated").as[Long] should beRecent
    invitationId
  }

  def checkClientsInvitation(client1Id: String, invitation: JsValue, testStartTime: Long): String = {

    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val alphanumeric = "[0-9A-Za-z]+"
    val invitationId = (invitation \ "id").as[String]
    invitationId should fullyMatch regex alphanumeric
    (invitation \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/clients/$client1Id/invitations/received/$invitationId"
    (invitation \ "_links" \ "cancel" \ "href").as[String] shouldBe s"/agent-client-authorisation/clients/$client1Id/invitations/received/$invitationId"
    (invitation \ "arn") shouldBe JsString(arn.arn)
    (invitation \ "regime") shouldBe JsString(REGIME)
    (invitation \ "clientRegimeId") shouldBe JsString(client1Id)
    (invitation \ "status") shouldBe JsString("Pending")
    (invitation \ "created").as[Long] should beRecent
    (invitation \ "lastUpdated").as[Long] should beRecent
    invitationId
  }

  def createInvitations: ((String, String), (String, String)) = {
    dropMongoDb()
    val agent = given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
    val client1Id = "1234567890"
    val client2Id = "1234567891"


    note("there should be no requests")
    eventually {
      inside(responseForGetInvitations()) { case resp =>
        resp.status shouldBe 200
        invitations(resp.json).value shouldBe 'empty
      }
    }

    note("we should be able to add 2 new requests")
    val location1: String = checkCreatedResponse(responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$client1Id", "postcode": "AA1 1AA"}"""))
    val location2: String = checkCreatedResponse(responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$client2Id", "postcode": "AA1 1AA"}"""))

    val json1: JsValue = new Resource(location1, port).get().json
    val json2: JsValue = new Resource(location2, port).get().json

    (invitation(json1), invitation(json2))
  }


  def createDuplicateInvitations: ((String, String), (String, String)) = {
    dropMongoDb()
    val agent = given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
    val client1Id = "1234567890"


    note("there should be no requests")
    eventually {
      inside(responseForGetInvitations()) { case resp =>
        resp.status shouldBe 200
        invitations(resp.json).value shouldBe 'empty
      }
    }

    note("we should be able to add 2 new requests")
    val location1: String = checkCreatedResponse(responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$client1Id", "postcode": "AA1 1AA"}"""))
    val location2: String = checkCreatedResponse(responseForCreateInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "$client1Id", "postcode": "AA1 1AA"}"""))

    val json1: JsValue = new Resource(location1, port).get().json
    val json2: JsValue = new Resource(location2, port).get().json

    (invitation(json1), invitation(json2))
  }

  "PUT /requests/:id/accept" is {
    pending
    //    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptRequest("request-id"))
  }

  def invitation(json: JsValue): (String, String) = {
      (json \ "id").as[String] ->
      (json \ "clientRegimeId").as[String]
  }

  def invitations(response: JsValue) = {
    val embedded = response \ "_embedded" \ "invitations"
    embedded match {
      case array: JsArray => array
      case obj: JsObject => JsArray(Seq(obj))
    }
  }

  def requestId(response: JsValue, clientId: String): String = {
    val req = invitations(response).value.filter(v => (v \ "clientRegimeId").as[String] == clientId).head
    (req \ "id").as[String]
  }

  def responseForAcceptRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests/$requestId/accept", port).postEmpty()
  }

  def responseForRejectRequest(requestId: String): HttpResponse = {
    new Resource(s"/agent-client-authorisation/requests/$requestId/reject", port).postEmpty()
  }

  def responseForGetClientInvitations(): HttpResponse = {
    new Resource(getClientInvitationsUrl, port).get()
  }

  def responseForGetClientInvitation(invitationId: String): HttpResponse = {
    new Resource(getClientInvitationUrl + invitationId, port).get()
  }

  def responseForGetInvitations(): HttpResponse = {
    new Resource(getInvitationsUrl, port).get()
  }

  def responseForGetInvitations(clientRegimeId: String): HttpResponse = {
    new Resource(getInvitationsUrl + s"?clientRegimeId=$clientRegimeId", port).get()
  }

  def responseForGetInvitation(invitationId: String = "none"): HttpResponse = {
    new Resource(getInvitationUrl + invitationId, port).get()
  }

  def responseForCreateInvitation(body: String): HttpResponse = {
    new Resource(createInvitationUrl, port).postAsJson(body)
}
  def checkCreatedResponse(httpResponse: HttpResponse) = {
    httpResponse.status shouldBe 201
    val expectedUri: String = httpResponse.header(LOCATION).get
    expectedUri should startWith (s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/")
    expectedUri
  }

  def aClientStatusChange(doStatusChangeRequest: String => HttpResponse) = {
    val ((_, client1Id), (invitation2Id,client2Id)) = createInvitations

    "return not found for an unknown request" in {
      doStatusChangeRequest("some-request-id").status shouldBe 404
    }

    "return forbidden for a request for a different user" in {
      val responseJson = responseForGetInvitations().json

      given().client().isLoggedIn(client1Id)

      val id: String = requestId(responseJson, client2Id)
      doStatusChangeRequest(id).status shouldBe 403
    }

    "return forbidden for a request not in Pending status" in {
      val responseJson = responseForGetInvitations().json

      given().client().isLoggedIn(client1Id)

      val id: String = requestId(responseJson, client1Id)
      doStatusChangeRequest(id).status shouldBe 200
      eventually {
        doStatusChangeRequest(id).status shouldBe 403
      }
    }
  }

}

