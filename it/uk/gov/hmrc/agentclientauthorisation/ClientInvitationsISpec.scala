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

import java.net.URI

import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.{FakeMtdClientId, MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class ClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val mtdClientId = FakeMtdClientId.random()
  private val mtdClient2Id = FakeMtdClientId.random()
  private val REGIME = "mtd-sa"

  private val createInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"
  private val getInvitationUrl = s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/"
  private def getInvitationsUrl(clientId: MtdClientId = mtdClientId) = s"/agent-client-authorisation/clients/${clientId.value}/invitations/received"



  "PUT of /clients/:clientId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptInvitation())

    "accept a pending request" in {
      val invitationUri = createInvitationForClient()

      given().client(clientId = mtdClientId).isLoggedIn()
        .aRelationshipIsCreatedWith(arn)

      acceptInvitation(invitationUri)
    }
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForRejectInvitation())

    "reject a pending request" in {
      val invitationId = createInvitationForClient()
      given().client(clientId = mtdClientId).isLoggedIn()

      val response = responseForRejectInvitation(invitationId)

      response.status shouldBe 204
    }

  }

  "GET /clients/:clientId/invitations/received" should {
    "return a 200 response" in {
      createInvitations()

      given().client(clientId = mtdClientId).isLoggedIn()

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received", port).get
      response.status shouldBe 200

      val json: JsValue = response.json
      val invitation = invitations(json)
      invitation.value.size shouldBe 1
    }

    "return more than one invitation" in {
      createInvitations()
      createInvitations()

      given().client(clientId = mtdClientId).isLoggedIn()

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received", port).get
      response.status shouldBe 200

      val json: JsValue = response.json
      val invitation = invitations(json)
      invitation.value.size shouldBe 2
    }

    "return only invitations with the specified status" in {
      val invitationUriToAccept = createInvitationForClient()
      createInvitation()

      given().client(clientId = mtdClientId).isLoggedIn()
        .aRelationshipIsCreatedWith(arn)
      acceptInvitation(invitationUriToAccept)

      given().client(clientId = mtdClientId).isLoggedIn()

      val acceptedInvitationsUrl = s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received?status=Accepted"
      val response = new Resource(acceptedInvitationsUrl, port).get
      response.status shouldBe 200

      val json: JsValue = response.json
      val invitation = invitations(json)
      invitation.value.size shouldBe 1

      (json \ "_links" \ "self" \ "href").as[String] shouldBe acceptedInvitationsUrl
    }

    "return a 401 response if not logged-in" in {

      given().client().isNotLoggedIn()
      responseForGetClientInvitations().status shouldBe 401
    }

    "return 404 when try to access someone else's invitations" in {
      createInvitations()

      given().client(clientId = mtdClientId).isLoggedIn()

      val response = new Resource(s"/agent-client-authorisation/clients/${mtdClient2Id.value}/invitations/received", port).get

      response.status shouldBe 403
    }
  }

  "GET /clients/:clientId/invitations/received/:invitation" should {
    "return a 200 response" in {

      val testStartTime = now().getMillis
      val invitationUri = createInvitationForClient()
      invitationUri.getPath should startWith(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/")

      given().client(clientId = mtdClientId).isLoggedIn()

      val response = responseForGetClientInvitation(invitationUri)
      response.status shouldBe 200

      val invitation = response.json
      checkInvitation(mtdClientId, invitation, testStartTime)
    }

    "return a 401 response if not logged-in" in {
      val invitationUri = createInvitationForClient()
      invitationUri.getPath should startWith(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/")

      given().client().isNotLoggedIn()
      responseForGetClientInvitation(invitationUri).status shouldBe 401
    }

    "return 404 when invitation not found" in {
      pending
      val response = responseForGetClientInvitation(new URI(getInvitationUrl + "none"))
      response.status shouldBe 404
    }

    "return 403 when try to access someone else's invitation" in {
      val invitationUri = createInvitationForClient(mtdClient2Id)

      given().client(clientId = mtdClientId).isLoggedIn()
      val response = responseForGetClientInvitation(invitationUri)
      response.status shouldBe 403
    }
  }

  def responseForGetClientInvitations(clientId: MtdClientId = mtdClientId): HttpResponse = {
    new Resource(getInvitationsUrl(clientId), port).get()
  }

  def responseForGetClientInvitation(invitationUri: URI): HttpResponse = {
    new Resource(invitationUri.toString, port).get()
  }

  private def createInvitation(body: String = s"""{"regime": "$REGIME", "clientId": "${mtdClientId.value}", "postcode": "AA1 1AA"}"""): Unit = {
    given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
    new Resource(createInvitationUrl, port).postAsJson(body)
  }

  private def createInvitationForClient(clientId: MtdClientId = mtdClientId): URI = {
    createInvitation(s"""{"regime": "$REGIME", "clientId": "${clientId.value}", "postcode": "AA1 1AA"}""")

    given().client(clientId = clientId).isLoggedIn()

    val response = responseForGetClientInvitations(clientId)
    response.status shouldBe 200

    val invitationsArray = invitations(response.json)
    invitationsArray.value.size shouldBe 1
    new URI(getInvitationsUrl(clientId)).resolve(
      (invitationsArray(0) \ "_links" \ "self" \ "href").as[String])
  }

  private def checkInvitation(clientId: MtdClientId, invitation: JsValue, testStartTime: Long): Unit = {
    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val selfHref = (invitation \ "_links" \ "self" \ "href").as[String]
    selfHref should startWith(s"/agent-client-authorisation/clients/${clientId.value}/invitations/received/")
    (invitation \ "_links" \ "accept" \ "href").as[String] shouldBe s"$selfHref/accept"
    (invitation \ "_links" \ "reject" \ "href").as[String] shouldBe s"$selfHref/reject"
    (invitation \ "arn").as[String] shouldBe arn.arn
    (invitation \ "regime").as[String] shouldBe REGIME
    (invitation \ "clientId").as[String] shouldBe clientId.value
    (invitation \ "status").as[String] shouldBe "Pending"
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }

  private def invitations(response: JsValue) = {
    (response \ "_embedded" \ "invitations").as[JsArray]
  }

  private def createInvitations(): Unit = {
    createInvitation()
    createInvitation(s"""{"regime": "$REGIME", "clientId": "${mtdClient2Id.value}", "postcode": "AA1 1AA"}""")
  }

  private def responseForRejectInvitation(invitationUri: URI = new URI(getInvitationUrl + "none")): HttpResponse = {
    new Resource(invitationUri.toString + "/reject", port).putEmpty()
  }

  private def responseForAcceptInvitation(invitationUri: URI = new URI(getInvitationUrl + "none")): HttpResponse = {
    new Resource(invitationUri.toString + "/accept", port).putEmpty()
  }

  private def acceptInvitation(invitationUri: URI): Unit = {
    val response = responseForAcceptInvitation(invitationUri)
    response.status shouldBe 204
  }
}
