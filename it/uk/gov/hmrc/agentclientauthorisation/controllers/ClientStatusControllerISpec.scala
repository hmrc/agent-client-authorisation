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
import org.joda.time.{DateTime, LocalDate}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class ClientStatusControllerISpec extends UnitSpec with AppAndStubs with MongoAppAndStubs with DesStubs {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])
  lazy val http = app.injector.instanceOf(classOf[Http])

  "GET /status" should {
    "return 200 OK indicating client has pending invitations if she has one for PIR" in {

      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships
      givenGetAgencyDetailsStub(Arn("TARN0000001"))
      givenGetAgencyDetailsStub(Arn("TARN0000002"))

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(repo.insert(invitation1))

      await(repo.update(invitation1, Expired, DateTime.now()))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.MtdIt,
        ClientIdentifier(Nino("AB992751D")),
        ClientIdentifier(MtdItId("KQFL80195230075")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(repo.insert(invitation2))

      val invitation3 = Invitation.createNew(
        Arn("TARN0000002"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now,
        LocalDate.now,
        None
      )
      await(repo.insert(invitation3))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe true
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe true
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has pending invitations if she has one for ITSA" in {

      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships
      givenGetAgencyDetailsStub(Arn("TARN0000001"))

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """{"allEnrolments":[{"key":"HMRC-MTD-IT","identifiers":[{"key":"MTDITID","value":"KQFL80195230075"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(repo.insert(invitation1))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.MtdIt,
        ClientIdentifier(Nino("AB992751D")),
        ClientIdentifier(MtdItId("KQFL80195230075")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(repo.insert(invitation2))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe true
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no pending invitations if she has one for ITSA and agent is suspended for ITSA" in {
      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships
      givenDESRespondsWithValidData(Arn("TARN0000001"), "agency name", SuspensionDetails(suspensionStatus = true, Some(Set("ITSA"))))

      stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse()
        .withStatus(200)
        .withBody(
          """{"allEnrolments":[{"key":"HMRC-MTD-IT","identifiers":[{"key":"MTDITID","value":"KQFL80195230075"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(repo.insert(invitation1))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no pending invitations when none exist" in {

      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673C")),
        ClientIdentifier(Nino("AB835673C")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )
      await(repo.insert(invitation))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no pending invitations when existing ones has expired" in {

      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships
      givenGetAgencyDetailsStub(Arn("TARN0000001"))
      givenGetAgencyDetailsStub(Arn("TARN0000002"))

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation1 = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now.minusDays(21),
        LocalDate.now,
        None
      )
      await(repo.insert(invitation1))
      await(repo.update(invitation1, Expired, DateTime.now()))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000002"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now.minusDays(15),
        LocalDate.now.minusDays(5),
        None
      )
      await(repo.insert(invitation2))
      await(repo.update(invitation2, Expired, DateTime.now()))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe true
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no invitation history when existing ones has expired and agent is suspended" in {
      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships
      givenDESRespondsWithValidData(Arn("TARN0000001"), "agency name", SuspensionDetails(suspensionStatus = true, Some(Set("VATC"))))

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation2 = Invitation.createNew(
        Arn("TARN0000002"),
        Some("personal"),
        Service.Vat,
        ClientIdentifier(Vrn("101747696")),
        ClientIdentifier(Vrn("101747696")),
        None,
        DateTime.now.minusDays(15),
        LocalDate.now.minusDays(5),
        None
      )
      await(repo.insert(invitation2))
      await(repo.update(invitation2, Expired, DateTime.now()))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no pending invitations when client has no expected enrolment(s)" in {

      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasNoActiveAfiRelationships

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"FOO","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val invitation = Invitation.createNew(
        Arn("TARN0000001"),
        Some("personal"),
        Service.PersonalIncomeRecord,
        ClientIdentifier(Nino("AB835673D")),
        ClientIdentifier(Nino("AB835673D")),
        None,
        DateTime.now,
        LocalDate.now.plusDays(21),
        None
      )

      await(repo.insert(invitation))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has active relationships " in {
      givenAuditConnector()
      givenClientHasActiveRelationshipsWith(Arn("TARN0000001"))
      givenClientHasNoActiveAfiRelationships

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe true
    }

    "return 200 OK indicating client has active fi relationship " in {
      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasActiveAfiRelationshipsWith(Arn("TARN0000001"))

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe true
    }

    "return 200 OK indicating client has no active relationships if afi returns 403" in {
      givenAuditConnector()
      givenClientHasNoActiveRelationships
      givenClientHasActiveAfiRelationshipsFails(Arn("TARN0000001"), 403)

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK indicating client has no active relationships if acr returns 403" in {
      givenAuditConnector()
      givenClientHasActiveRelationshipsFails(Arn("TARN0000001"), 403)
      givenClientHasNoActiveAfiRelationships

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody("""{"allEnrolments":[{"key":"HMRC-NI","identifiers":[{"key":"NINO","value":"AB835673D"}]}]}""")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 200 OK even if user is not Individual nor Organisation" in {
      givenAuditConnector()

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"UnsupportedAffinityGroup\"")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 200

      val json = response.json
      (json \ "hasPendingInvitations").as[Boolean] shouldBe false
      (json \ "hasInvitationsHistory").as[Boolean] shouldBe false
      (json \ "hasExistingRelationships").as[Boolean] shouldBe false
    }

    "return 401 Unauthorized f user is not authenticated" in {
      givenAuditConnector()

      stubFor(
        post(urlPathEqualTo(s"/auth/authorise")).willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"MissingBearerToken\"")))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/status", port, http).get()
      response.status shouldBe 401
    }

  }

  def givenClientHasNoActiveRelationships =
    stubFor(
      get(urlPathEqualTo("/agent-client-relationships/client/relationships/active")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody("{}")))

  def givenClientHasNoActiveAfiRelationships =
    stubFor(
      get(urlPathEqualTo("/agent-fi-relationship/relationships/active")).willReturn(aResponse()
        .withStatus(404)))

  def givenClientHasActiveRelationshipsWith(arn: Arn) =
    stubFor(
      get(urlPathEqualTo("/agent-client-relationships/client/relationships/active")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{
                       | "HMRC-MTD-IT": ["${arn.value}"],
                       | "HMRC-MTD-VAT": ["${arn.value}"]
                       |}""".stripMargin)))

  def givenClientHasActiveRelationshipsFails(arn: Arn, status: Int) =
    stubFor(
      get(urlPathEqualTo("/agent-client-relationships/client/relationships/active")).willReturn(aResponse()
        .withStatus(status)))

  def givenClientHasActiveAfiRelationshipsWith(arn: Arn) =
    stubFor(
      get(urlPathEqualTo("/agent-fi-relationship/relationships/active")).willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""[
                       |  {
                       |    "arn": "${arn.value}",
                       |    "service": "service123",
                       |    "clientId": "clientId123",
                       |    "relationshipStatus": "Active"
                       |  }
                       |]""".stripMargin)))

  def givenClientHasActiveAfiRelationshipsFails(arn: Arn, status: Int) =
    stubFor(
      get(urlPathEqualTo("/agent-fi-relationship/relationships/active")).willReturn(aResponse()
        .withStatus(status)))
}
