/*
 * Copyright 2018 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, contentAsJson, defaultAwaitTimeout}
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientStatusController.ClientStatus
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, PartialAuth}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino

import java.time.{LocalDate, LocalDateTime}

class ClientStatusWithACRControllerISpec extends UnitSpec with AppAndStubs with MongoAppAndStubs with DesStubs with ACRStubs {

  override protected def additionalConfiguration: Map[String, Any] =
    super.additionalConfiguration + ("acr-mongo-activated" -> true)

  lazy val invitationRepo: InvitationsRepositoryImpl = app.injector.instanceOf[InvitationsRepositoryImpl]
  lazy val controller: ClientStatusController = app.injector.instanceOf[ClientStatusController]

  val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest().withHeaders("Authorization" -> "Bearer testtoken")

  "GET /status" should {
    "return 200 OK forwarding the ACR response when all three flags are true" in {
      givenAuditConnector()
      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")
        )
      )
      stubGetCustomerStatus(ClientStatus(hasPendingInvitations = true, hasInvitationsHistory = true, hasExistingRelationships = true))

      val response = controller.getStatus(fakeRequest)
      status(response) shouldBe 200
      contentAsJson(response) shouldBe Json.toJson(
        ClientStatus(hasPendingInvitations = true, hasInvitationsHistory = true, hasExistingRelationships = true)
      )
    }

    "return 200 OK after only rechecking invitations when ACR only finds a relationship" in {
      givenAuditConnector()
      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")
        )
      )
      givenGetAgencyDetailsStub(Arn("TARN0000001"))
      stubGetCustomerStatus(ClientStatus(hasExistingRelationships = true))

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.MtdIt,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        LocalDateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(invitationRepo.collection.insertOne(invitation).toFuture())

      val response = controller.getStatus(fakeRequest)
      status(response) shouldBe 200
      contentAsJson(response) shouldBe Json.toJson(
        ClientStatus(hasPendingInvitations = true, hasExistingRelationships = true)
      )
    }

    "return 200 OK after only rechecking partial auth when ACR does not find a relationship" in {
      givenAuditConnector()
      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")
        )
      )
      givenGetAgencyDetailsStub(Arn("TARN0000001"))
      stubGetCustomerStatus(ClientStatus())

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.MtdIt,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        LocalDateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(invitationRepo.collection.insertOne(invitation).toFuture())
      await(invitationRepo.update(invitation, PartialAuth, LocalDateTime.now()))

      val response = controller.getStatus(fakeRequest)
      status(response) shouldBe 200
      contentAsJson(response) shouldBe Json.toJson(
        ClientStatus(hasInvitationsHistory = true, hasExistingRelationships = true)
      )
    }
  }
}
