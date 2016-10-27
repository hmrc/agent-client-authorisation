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

import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource, SecuredEndpointBehaviours}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class ClientInvitationsISpec extends UnitSpec with MongoAppAndStubs with SecuredEndpointBehaviours {

  private implicit val arn = Arn("ABCDEF12345678")
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val mtdClientId = MtdClientId("MTD-0123456789")
  private val invitationId = "12345-654321"
  private val REGIME = "mtd-sa"

  private val createInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"
  private val getInvitationUrl = s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/"
  "PUT of /clients/:clientId/invitations/received/:invitationId/accept" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForAcceptInvitation())

    "accept a pending request" in {
      val invitationId = createInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "${mtdClientId.value}", "postcode": "AA1 1AA"}""")

      given().client().isLoggedIn("0123456789")
        .aRelationshipIsCreatedWith(arn)

      val response = responseForAcceptInvitation(invitationId)

      response.status shouldBe 204
    }
  }

  "PUT of /clients/:clientId/invitations/received/:invitationId/reject" should {
    behave like anEndpointAccessibleForSaClientsOnly(responseForRejectInvitation())

    "reject a pending request" in {
      val invitationId = createInvitation(s"""{"regime": "$REGIME", "clientRegimeId": "${mtdClientId.value}", "postcode": "AA1 1AA"}""")
      given().client().isLoggedIn("0123456789")

      val response = responseForRejectInvitation(invitationId)

      response.status shouldBe 204
    }

  }

  def createInvitation(body: String): String = {
    given().agentAdmin(arn, agentCode).isLoggedIn().andHasMtdBusinessPartnerRecord()
    val response = new Resource(createInvitationUrl, port).postAsJson(body)

    response.status shouldBe 201
    val location = response.header("location").get

    val invitation = new Resource(location, port).get.json

    (invitation \ "id").as[String]
  }


  def responseForRejectInvitation(invitationId: String = "none"): HttpResponse = {
    new Resource(getInvitationUrl + invitationId + "/reject", port).putEmpty()
  }

  def responseForAcceptInvitation(invitationId: String = "none"): HttpResponse = {
    new Resource(getInvitationUrl + invitationId + "/accept", port).putEmpty()
  }
}
