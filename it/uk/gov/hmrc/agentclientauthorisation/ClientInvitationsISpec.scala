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

import org.joda.time.DateTime.now
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsObject, JsString, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class ClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val saUtr: String = "0123456789"
  private val mtdClientId = MtdClientId("MTD-" + saUtr)
  private val mtdClient2Id = MtdClientId("MTD-9876543210")
  private val REGIME = "mtd-sa"

  private val createInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"
  private val getInvitationUrl = s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/"
  private val getInvitationsUrl = s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received"
  "PUT of /clients/:clientId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptInvitation())

    "accept a pending request" in {
      val invitationId = createInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "${mtdClientId.value}", "postcode": "AA1 1AA"}""")

      given().client().isLoggedIn(saUtr)
        .aRelationshipIsCreatedWith(arn)

      val response = responseForAcceptInvitation(invitationId)

      response.status shouldBe 204
    }
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForRejectInvitation())

    "reject a pending request" in {
      val invitationId = createInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "${mtdClientId.value}", "postcode": "AA1 1AA"}""")
      given().client().isLoggedIn(saUtr)

      val response = responseForRejectInvitation(invitationId)

      response.status shouldBe 204
    }

  }

  "GET /clients/:clientId/invitations/received" should {
    "return a 200 response" in {
      createInvitations()

      given().client().isLoggedIn(saUtr)

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received", port).get
      response.status shouldBe 200

      val json: JsValue = response.json
      val invitation = invitations(json)
      invitation.value.size shouldBe 1
    }

    "return more than one invitation" in {
      createInvitations()
      createInvitations()

      given().client().isLoggedIn(saUtr)

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received", port).get
      response.status shouldBe 200

      val json: JsValue = response.json
      val invitation = invitations(json)
      invitation.value.size shouldBe 2
    }

    "return a 401 response if not logged-in" in {

      given().client().isNotLoggedIn()
      responseForGetClientInvitations().status shouldBe 401
    }

    "return 404 when try to access someone else's invitations" in {
      createInvitations()

      given().client().isLoggedIn(saUtr)

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClient2Id.value}/invitations/received", port).get

      response.status shouldBe 403
    }
  }

  "GET /clients/:clientId/invitations/received/:invitation" should {
    "return a 200 response" in {

      val testStartTime = now().getMillis
      val (invitation1Id, _) = createInvitations()

      given().client().isLoggedIn(saUtr)

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/$invitation1Id", port).get
      response.status shouldBe 200

      val invitation = response.json
      checkInvitation(mtdClientId, invitation, testStartTime)
    }

    "return a 401 response if not logged-in" in {
      val (invitation1Id, _) = createInvitations()

      given().client().isNotLoggedIn()
      responseForGetClientInvitation(invitation1Id).status shouldBe 401
    }

    "return 404 when invitation not found" in {

      val response = responseForGetClientInvitation("none")
      response.status shouldBe 404
    }

    "return 403 when try to access someone else's invitation" in {
      val (_, invitation2Id) = createInvitations()

      given().client().isLoggedIn(saUtr)
      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClient2Id.value}/invitations/received/$invitation2Id", port).get
      response.status shouldBe 403
    }
  }

  def responseForGetClientInvitations(): HttpResponse = {
    new Resource(getInvitationsUrl, port).get()
  }

  def responseForGetClientInvitation(invitationId: String): HttpResponse = {
    new Resource(getInvitationUrl + invitationId, port).get()
  }

  private def createInvitation(body: String): String = {
    given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
    val response = new Resource(createInvitationUrl, port).postAsJson(body)

    response.status shouldBe 201
    val location = response.header("location").get

    val invitation = new Resource(location, port).get.json

    (invitation \ "id").as[String]
  }

  private def checkInvitation(clientId: MtdClientId, invitation: JsValue, testStartTime: Long): String = {
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val alphanumeric = "[0-9A-Za-z]+"
    val invitationId = (invitation \ "id").as[String]
    invitationId should fullyMatch regex alphanumeric
    (invitation \ "_links" \ "self" \ "href").as[String] shouldBe s"/agent-client-authorisation/clients/${clientId.value}/invitations/received/$invitationId"
    (invitation \ "_links" \ "accept" \ "href").as[String] shouldBe s"/agent-client-authorisation/clients/${clientId.value}/invitations/received/$invitationId/accept"
    (invitation \ "_links" \ "reject" \ "href").as[String] shouldBe s"/agent-client-authorisation/clients/${clientId.value}/invitations/received/$invitationId/reject"
    (invitation \ "arn") shouldBe JsString(arn.arn)
    (invitation \ "regime") shouldBe JsString(REGIME)
    (invitation \ "clientRegimeId") shouldBe JsString(clientId.value)
    (invitation \ "status") shouldBe JsString("Pending")
    (invitation \ "created").as[Long] should beRecent
    (invitation \ "lastUpdated").as[Long] should beRecent
    invitationId
  }

  private def invitations(response: JsValue) = {
    (response \ "_embedded" \ "invitations").as[JsArray]
  }

  def createInvitations(): (String, String) = {
    val id1 = createInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "${mtdClientId.value}", "postcode": "AA1 1AA"}""")
    val id2 = createInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "${mtdClient2Id.value}", "postcode": "AA1 1AA"}""")

    (id1, id2)
  }
  def responseForRejectInvitation(invitationId: String = "none"): HttpResponse = {
    new Resource(getInvitationUrl + invitationId + "/reject", port).putEmpty()
  }

  def responseForAcceptInvitation(invitationId: String = "none"): HttpResponse = {
    new Resource(getInvitationUrl + invitationId + "/accept", port).putEmpty()
  }
}
