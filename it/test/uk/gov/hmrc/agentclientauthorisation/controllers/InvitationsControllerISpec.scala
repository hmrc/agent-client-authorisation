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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, post, stubFor, urlPathEqualTo}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, Service}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse

import java.time.{LocalDate, LocalDateTime}

class InvitationsControllerISpec extends UnitSpec with MongoAppAndStubs with DesStubs {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])
  lazy val http = app.injector.instanceOf(classOf[Http])

  "GET /invitations/:id" should {
    "return 200 OK with the invitation if found" in {

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody("{}")))
      givenGetAgencyDetailsStub(Arn("TARN0000001"))

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        LocalDateTime.now,
        LocalDate.now,
        None
      )

      await(repo.collection.insertOne(invitation).toFuture())

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/invitations/${invitation.invitationId.value}", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "arn").as[String] shouldBe "TARN0000001"
      (json \ "clientType").as[String] shouldBe "personal"
      (json \ "service").as[String] shouldBe "PERSONAL-INCOME-RECORD"
      (json \ "clientId").as[String] shouldBe "AB835673D"
      (json \ "clientIdType").as[String] shouldBe "ni"
      (json \ "suppliedClientId").as[String] shouldBe "AB835673D"
      (json \ "suppliedClientIdType").as[String] shouldBe "ni"
    }

    "return 404 NOT FOUND when invitation doesn't exist" in {

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody("{}")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/invitations/foo", port, http).get()
      response.status shouldBe 404
    }

    "return 401 when user not authorised" in {

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(401).withBody("{}")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/invitations/foo", port, http).get()
      response.status shouldBe 401
    }
  }
}
