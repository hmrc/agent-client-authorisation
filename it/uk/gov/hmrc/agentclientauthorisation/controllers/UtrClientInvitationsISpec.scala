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

import org.joda.time.{DateTime, LocalDate}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class UtrClientInvitationsISpec
    extends UnitSpec with MongoAppAndStubs with AuthStubs with DesStubs with HttpResponseUtils with RelationshipStubs {

  lazy val repo = app.injector.instanceOf(classOf[InvitationsRepository])

  val arn = "TARN0000001"
  val mtdItId = "YNIZ22082177289"
  val nino = "AB123456A"
  val vrn = "660304567"
  val utr = "9246624558"
  val eori = "AQ886940109600"

  "PUT /clients/UTR/:utr/invitations/received/:invitationId/accept" should {
    "return 204 when invitation exists and can be accepted" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)
      //
      givenNiOrgRelationshipIsCreatedWith(arn, eori)
      val invitation = givenPendingInvitation(arn, utr)

      ///clients/UTR/:utr/invitations/received/:invitationId/accept
      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/${invitation.invitationId.value}/accept",
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
          s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/${invitation.invitationId.value}/accept",
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
          s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/${invitation.invitationId.value}/accept",
          port)
          .putEmpty()

      response.status shouldBe 403
      verifyInvitationStatusIs(Expired, invitation)
    }

    "return 404 when invitation cannot be found" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/EUXU4F44JLDXZ/accept", port)
          .putEmpty()

      response.status shouldBe 404
    }
  }

  "PUT /clients/UTR/:utr/invitations/received/:invitationId/reject" should {
    "return 204 when invitation exists and can be rejected" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)
      val invitation = givenPendingInvitation(arn, utr)

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/${invitation.invitationId.value}/reject",
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
          s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/${invitation.invitationId.value}/reject",
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
          s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/${invitation.invitationId.value}/reject",
          port)
          .putEmpty()

      response.status shouldBe 403
      verifyInvitationStatusIs(Expired, invitation)
    }

    "return 404 when invitation cannot be found" in {
      givenAuditConnector()
      givenAuthorisedAsClient("HMRC-NI-ORG", "NIEORI", eori)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/clients/UTR/$utr/invitations/received/EUXU4F44JLDXZ/reject", port)
          .putEmpty()

      response.status shouldBe 404
    }
  }

  def givenPendingInvitation(arn: String, utr: String): Invitation =
    await(
      repo.create(
        Arn(arn),
        Some("personal"),
        Service.NiOrgNotEnrolled,
        Utr(utr),
        Utr(utr),
        DateTime.now,
        LocalDate.now.plusDays(10)))

  def verifyInvitationStatusIs(status: InvitationStatus, invitation: Invitation) =
    await(repo.find("invitationId" -> invitation.invitationId).map(_.headOption.map(_.status))) shouldBe Some(status)
}
