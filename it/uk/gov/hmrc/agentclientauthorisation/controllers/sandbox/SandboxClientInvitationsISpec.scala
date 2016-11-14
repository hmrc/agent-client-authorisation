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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import java.net.URI

import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.scalatest.Inside
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsString, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.{FakeMtdClientId, MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class SandboxClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours with Eventually with Inside {
  private val REGIME = "mtd-sa"
  private implicit val arn = Arn("ABCDEF12345678")
  private val mtdClientId = FakeMtdClientId.random()
  private val getInvitationUrl = s"/agent-client-authorisation/sandbox/clients/${mtdClientId.value}/invitations/received/"
  private val getInvitationsUrl = s"/agent-client-authorisation/sandbox/clients/${mtdClientId.value}/invitations/received"

  "PUT of /sandbox/clients/:clientId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptInvitation())

    "return a 204 response code" in {
      given().client(clientId = mtdClientId).isLoggedIn()

      val response = responseForAcceptInvitation()

      response.status shouldBe 204
    }
  }

  "PUT of /sandbox/clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForRejectInvitation())

    "return a 204 response code" in {
      given().client(clientId = mtdClientId).isLoggedIn()

      val response = responseForRejectInvitation()

      response.status shouldBe 204
    }
  }

  "GET /clients/:clientId/invitations/received" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForGetClientInvitations())

    "return some invitations" in {
      val testStartTime = now().getMillis
      given().client(clientId = mtdClientId).isLoggedIn()
      
      val response = responseForGetClientInvitations()

      response.status shouldBe 200
      val invitations = (response.json \ "_embedded" \ "invitations").as[JsArray].value
      val selfLinkHref = (response.json \ "_links" \ "self" \ "href").as[String]

      invitations.size shouldBe 2
      checkInvitation(mtdClientId, invitations.head, testStartTime) 
      checkInvitation(mtdClientId, invitations(1), testStartTime) 
      selfLinkHref shouldBe getInvitationsUrl
    }
  }

  "GET /clients/:clientId/invitations/received/invitationId" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForGetClientInvitation())

    "return an invitation" in {
      val testStartTime = now().getMillis
      given().client(clientId = mtdClientId).isLoggedIn()
      
      val response = responseForGetClientInvitation()

      response.status shouldBe 200
      checkInvitation(mtdClientId, response.json, testStartTime) 
    }
  }
  
  private def checkInvitation(clientId: MtdClientId, invitation: JsValue, testStartTime: Long): Unit = {
    implicit val dateTimeRead = RestFormats.dateTimeRead
    val beRecent = be >= testStartTime and be <= (testStartTime + 5000)
    val selfHref = (invitation \ "_links" \ "self" \ "href").as[String]
    selfHref should startWith(s"/agent-client-authorisation/sandbox/clients/${clientId.value}/invitations/received/")
    (invitation \ "_links" \ "accept" \ "href").as[String] shouldBe s"$selfHref/accept"
    (invitation \ "_links" \ "reject" \ "href").as[String] shouldBe s"$selfHref/reject"
    (invitation \ "_links" \ "agency").asOpt[String] shouldBe None
    (invitation \ "arn") shouldBe JsString("agencyReference")
    (invitation \ "regime") shouldBe JsString(REGIME)
    (invitation \ "clientId") shouldBe JsString(clientId.value)
    (invitation \ "status") shouldBe JsString("Pending")
    (invitation \ "created").as[DateTime].getMillis should beRecent
    (invitation \ "lastUpdated").as[DateTime].getMillis should beRecent
  }

  private def invitations(response: JsValue) = {
    (response \ "_embedded" \ "invitations").as[JsArray]
  }

  def responseForGetClientInvitation(): HttpResponse = {
    new Resource(getInvitationUrl + "invitationId", port).get()
  }

  def responseForGetClientInvitations(): HttpResponse = {
    new Resource(getInvitationsUrl, port).get()
  }

  private def responseForRejectInvitation(invitationUri: URI = new URI(getInvitationUrl + "none")): HttpResponse = {
    new Resource(invitationUri.toString + "/reject", port).putEmpty()
  }

  private def responseForAcceptInvitation(invitationUri: URI = new URI(getInvitationUrl + "none")): HttpResponse = {
    new Resource(invitationUri.toString + "/accept", port).putEmpty()
  }
}
