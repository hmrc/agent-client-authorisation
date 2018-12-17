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
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, Invitation, Pending, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ClientStatusControllerISpec extends UnitSpec with MongoAppAndStubs {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepository])

  "GET /status" should {
    "return 200 OK indicating client has pending invitations if she has one for PIR" in {

      givenAuditConnector()

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        DateTime.now,
        LocalDate.now.plusDays(10)
      )
      await(repo.insert(invitation1))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000001"),
        Service.MtdIt,
        ClientIdentifier(Nino("AB992751D")),
        ClientIdentifier(MtdItId("KQFL80195230075")),
        DateTime.now,
        LocalDate.now.plusDays(10)
      )
      await(repo.insert(invitation2))

      val invitation3 = Invitation.createNew(
        Arn("TARN0000002"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        DateTime.now,
        LocalDate.now
      )
      await(repo.insert(invitation3))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe true
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe true
    }

    "return 200 OK indicating client has pending invitations if she has one for ITSA" in {

      givenAuditConnector()

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """{"allEnrolments":[{"key":"HMRC-MTD-IT","identifiers":[{"key":"MTDITID","value":"KQFL80195230075"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        DateTime.now,
        LocalDate.now.plusDays(10)
      )
      await(repo.insert(invitation1))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000001"),
        Service.MtdIt,
        ClientIdentifier(Nino("AB992751D")),
        ClientIdentifier(MtdItId("KQFL80195230075")),
        DateTime.now,
        LocalDate.now.plusDays(10)
      )
      await(repo.insert(invitation2))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe true
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no pending invitations when none exist" in {

      givenAuditConnector()

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673C")),
        ClientIdentifier(Nino("AB835673C")),
        DateTime.now,
        LocalDate.now.plusDays(10)
      )
      await(repo.insert(invitation))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no pending invitations when existing ones has expired" in {

      givenAuditConnector()

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        DateTime.now.minusDays(10),
        LocalDate.now
      )
      await(repo.insert(invitation1))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000002"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        DateTime.now.minusDays(15),
        LocalDate.now.minusDays(5)
      )
      await(repo.insert(invitation2))
      await(repo.update(invitation2, Pending, DateTime.now.minusDays(4)))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe true
    }

    "return 200 OK indicating client has no pending invitations when client has no expected enrolment(s)" in {

      givenAuditConnector()

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"FOO","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        DateTime.now,
        LocalDate.now.plusDays(10)
      )

      await(repo.insert(invitation))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
    }

  }
}
