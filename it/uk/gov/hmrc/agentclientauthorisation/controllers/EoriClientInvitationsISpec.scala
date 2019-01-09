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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, put, stubFor, urlEqualTo}
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JsObject
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Eori}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class EoriClientInvitationsISpec
    extends UnitSpec with MongoAppAndStubs with AuthStubs with DesStubs with HttpResponseUtils with RelationshipStubs {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepository])
  lazy val controller = app.injector.instanceOf(classOf[AgencyInvitationsController])

  val arn = "TARN0000001"
  val mtdItId = "YNIZ22082177289"
  val nino = "AB123456A"
  val vrn = "660304567"
  val utr = "9246624558"
  val eori = "AQ886940109600"

  "PUT /clients/EORI/:eori/invitations/received/:invitationId/accept" should {
    "return 204 when invitation exists and can be accepted" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)
      givenNiOrgRelationshipIsCreatedWith(arn, eori)
      val invitation = givenPendingInvitation(arn, eori)

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}/accept",
          port)
          .putEmpty()

      response.status shouldBe 204
      verifyInvitationStatusIs(Accepted, invitation)
    }

    "return 403 when user is not enrolled for HMRC-NI-ORG" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-MTD-IT", "MTDITID", mtdItId)
      val invitation = givenPendingInvitation(arn, eori)

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}/accept",
          port)
          .putEmpty()

      response.status shouldBe 403
      verifyInvitationStatusIs(Pending, invitation)
    }

    "return 403 when invitation cannot be accepted" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val invitation = givenPendingInvitation(arn, eori)
      await(repo.update(invitation, Expired, DateTime.now))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}/accept",
          port)
          .putEmpty()

      response.status shouldBe 403
      verifyInvitationStatusIs(Expired, invitation)
    }

    "return 404 when invitation cannot be found" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/AUXU4F44JLDXZ/accept", port)
          .putEmpty()

      response.status shouldBe 404
    }
  }

  "PUT /clients/EORI/:eori/invitations/received/:invitationId/reject" should {
    "return 204 when invitation exists and can be rejected" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)
      val invitation = givenPendingInvitation(arn, eori)

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}/reject",
          port)
          .putEmpty()

      response.status shouldBe 204
      verifyInvitationStatusIs(Rejected, invitation)
    }

    "return 403 when user is not enrolled for HMRC-NI-ORG" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-MTD-IT", "MTDITID", mtdItId)
      val invitation = givenPendingInvitation(arn, eori)

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}/reject",
          port)
          .putEmpty()

      response.status shouldBe 403
      verifyInvitationStatusIs(Pending, invitation)
    }

    "return 403 when invitation cannot be accepted" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val invitation = givenPendingInvitation(arn, eori)
      await(repo.update(invitation, Expired, DateTime.now))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}/reject",
          port)
          .putEmpty()

      response.status shouldBe 403
      verifyInvitationStatusIs(Expired, invitation)
    }

    "return 404 when invitation cannot be found" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/AUXU4F44JLDXZ/reject", port)
          .putEmpty()

      response.status shouldBe 404
    }

  }

  "GET /clients/EORI/:eori/invitations/received/:invitationId" should {
    "return 200 when invitation exists" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)
      val invitation = givenPendingInvitation(arn, eori)
      await(repo.update(invitation, Accepted, DateTime.now))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}",
          port).get

      response.status shouldBe 200
      val responseJson = response.json.as[JsObject]

      (responseJson \ "arn").as[String] shouldBe arn
      (responseJson \ "service").as[String] shouldBe "HMRC-NI-ORG"
      (responseJson \ "status").as[String] shouldBe "Accepted"
      (responseJson \ "clientId").as[String] shouldBe eori
      (responseJson \ "clientIdType").as[String] shouldBe "eori"
      (responseJson \ "suppliedClientId").as[String] shouldBe eori
      (responseJson \ "suppliedClientIdType").as[String] shouldBe "eori"
    }

    "return 403 when user is not enrolled for HMRC-NI-ORG" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-MTD-IT", "MTDITID", mtdItId)
      val invitation = givenPendingInvitation(arn, eori)

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/${invitation.invitationId.value}",
          port).get

      response.status shouldBe 403
      verifyInvitationStatusIs(Pending, invitation)
    }

    "return 404 when invitation cannot be found" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/EORI/$eori/invitations/received/AUXU4F44JLDXZ", port).get

      response.status shouldBe 404
    }
  }

  "GET /clients/EORI/:eori/invitations/received" should {

    val arn2 = "TARN0000002"
    val arn3 = "TARN0000003"
    val eori2 = "AQ886940109601"

    "return 200 when invitation exists" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)
      val invitation1 = givenPendingInvitation(arn, eori)
      val invitation2 = givenPendingInvitation(arn2, eori)
      val invitation3 = givenPendingInvitation(arn3, eori)
      givenPendingInvitation(arn, eori2)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/EORI/$eori/invitations/received", port).get

      response.status shouldBe 200
      val responseJson = response.json.as[JsObject]

      (responseJson \ "_embedded" \ "invitations")
        .as[Seq[JsObject]]
        .map(_ \ "invitationId")
        .map(_.as[String]) should contain
        .only(invitation1.invitationId.value, invitation2.invitationId.value, invitation3.invitationId.value)
    }

    "return 403 when user is not enrolled for HMRC-NI-ORG" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-MTD-IT", "MTDITID", mtdItId)
      givenPendingInvitation(arn, eori)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/EORI/$eori/invitations/received", port).get

      response.status shouldBe 403
    }
  }

  def givenPendingInvitation(arn: String, eori: String): Invitation =
    await(
      repo.create(
        Arn(arn),
        Some("personal"),
        Service.NiOrgEnrolled,
        Eori(eori),
        Eori(eori),
        DateTime.now,
        LocalDate.now.plusDays(10)))

  def verifyInvitationStatusIs(status: InvitationStatus, invitation: Invitation) =
    await(repo.find("invitationId" -> invitation.invitationId).map(_.headOption.map(_.status))) shouldBe Some(status)
}

trait RelationshipStubs {

  def givenNiOrgRelationshipIsCreatedWith(arn: String, clientId: String) =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/agent/$arn/service/HMRC-NI-ORG/client/EORI/$clientId"))
        .willReturn(aResponse().withStatus(201)))
}
