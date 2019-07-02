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
import org.joda.time.{DateTime, LocalDate}
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentclientauthorisation.support.{AgentServicesAccountStub, MongoAppAndStubs, Resource}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class InvitationsControllerISpec extends UnitSpec with MongoAppAndStubs with AgentServicesAccountStub {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  "GET /invitations/:id" should {
    "return 200 OK with the invitation if found" in {

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody("{}")))
      givenGetAgencyNameAgentStub

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now,
        LocalDate.now
      )

      await(repo.insert(invitation))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/invitations/${invitation.invitationId.value}", port).get()
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
        new Resource(s"/agent-client-authorisation/invitations/foo", port).get()
      response.status shouldBe 404
    }

    "return 401 when user not authorised" in {

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(401).withBody("{}")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/invitations/foo", port).get()
      response.status shouldBe 401
    }
  }
}
