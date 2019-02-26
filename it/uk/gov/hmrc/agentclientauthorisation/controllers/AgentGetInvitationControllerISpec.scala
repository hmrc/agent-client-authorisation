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

import akka.stream.Materializer
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.{JsArray, JsObject}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.NoPermissionOnAgency
import uk.gov.hmrc.agentclientauthorisation.model.Service.PersonalIncomeRecord
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}

import scala.concurrent.ExecutionContext.Implicits.global

class AgentGetInvitationControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepository])

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
  }

  val clientIdentifierVat = ClientIdentifier("101747696", VrnType.id)

  "GET /agencies/:arn/invitations/sent" should {

    val request = FakeRequest("GET", "/agencies/:arn/invitations/sent")
    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)
    val clientIdentifierIrv = ClientIdentifier("AA000003D", NinoType.id)

    "return 200 with an invitation entity for an authorised agent with no query parameters" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn.value
      (json \ "clientType").as[String] shouldBe "personal"
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe "FOO"
      (json \ "clientIdType").as[String] shouldBe "MTDITID"
      (json \ "suppliedClientId").as[String] shouldBe "FOO"
      (json \ "suppliedClientIdType").as[String] shouldBe "MTDITID"
      (json \ "clientActionUrl").as[String].matches("(\\/invitations\\/personal\\/[A-Z0-9]{8}\\/my-agency)") shouldBe true
    }

    "return 200 with an invitation entity for an authorised agent with query parameters" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      await(
        invitationsRepo.create(
          arn,
          Some("business"),
          Service.Vat,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.PersonalIncomeRecord,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn, Some("personal"), Some("HMRC-MTD-IT"), None, Some("FOO"), None, Some(LocalDate.now()))(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val jsonInvitations = (jsonResponse \ "_embedded" \ "invitations").as[JsArray]
      val jsonInvitation = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]

      jsonInvitations.value.size shouldBe 1
      (jsonInvitation \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (jsonInvitation \ "arn").as[String] shouldBe arn.value
      (jsonInvitation \ "clientType").as[String] shouldBe "personal"
      (jsonInvitation \ "status").as[String] shouldBe "Pending"
      (jsonInvitation \ "clientId").as[String] shouldBe "FOO"
      (jsonInvitation \ "clientIdType").as[String] shouldBe "MTDITID"
      (jsonInvitation \ "suppliedClientId").as[String] shouldBe "FOO"
      (jsonInvitation \ "suppliedClientIdType").as[String] shouldBe "MTDITID"
      (jsonInvitation \ "clientActionUrl").as[String].matches("(\\/invitations\\/personal\\/[A-Z0-9]{8}\\/my-agency)") shouldBe true

    }

    "return 200 with empty invitation array when no invitations are found for this arn" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn2, None, None, None, None, None, None)(request)

      status(response) shouldBe 200
      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      json shouldBe empty
    }

    "return 200 with ITSA invitation without client type and add clientActionUrl" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          None,
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200
      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val jsonInvitations = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      jsonInvitations.value.size shouldBe 1
      val jsonInvitation = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]
      (jsonInvitation \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (jsonInvitation \ "arn").as[String] shouldBe arn.value
      (jsonInvitation \ "clientType").asOpt[String] shouldBe None
      (jsonInvitation \ "status").as[String] shouldBe "Pending"
      (jsonInvitation \ "clientActionUrl").as[String].matches("(\\/invitations\\/personal\\/[A-Z0-9]{8}\\/my-agency)") shouldBe true
    }

    "return 200 with IRV invitation without client type and add clientActionUrl" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          None,
          Service.PersonalIncomeRecord,
          clientIdentifierIrv,
          clientIdentifierIrv,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200
      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val jsonInvitations = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      jsonInvitations.value.size shouldBe 1
      val jsonInvitation = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]
      (jsonInvitation \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (jsonInvitation \ "arn").as[String] shouldBe arn.value
      (jsonInvitation \ "clientType").asOpt[String] shouldBe None
      (jsonInvitation \ "status").as[String] shouldBe "Pending"
      (jsonInvitation \ "clientActionUrl").as[String].matches("(\\/invitations\\/personal\\/[A-Z0-9]{8}\\/my-agency)") shouldBe true
    }

    "return 200 with VAT invitation without client type and add clientActionUrl" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          None,
          Service.Vat,
          clientIdentifierVat,
          clientIdentifierVat,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200
      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val jsonInvitations = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      jsonInvitations.value.size shouldBe 1
      val jsonInvitation = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]
      (jsonInvitation \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (jsonInvitation \ "arn").as[String] shouldBe arn.value
      (jsonInvitation \ "clientType").asOpt[String] shouldBe None
      (jsonInvitation \ "status").as[String] shouldBe "Pending"
      (jsonInvitation \ "clientActionUrl").as[String].matches("(\\/invitations\\/business\\/[A-Z0-9]{8}\\/my-agency)") shouldBe true
    }

    "return 200 with Accepted VAT invitation without client type" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          None,
          Service.Vat,
          clientIdentifierVat,
          clientIdentifierVat,
          DateTime.now(),
          LocalDate.now()))

      await(invitationsRepo.update(invitation, Accepted, DateTime.now()))

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200
      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val jsonInvitations = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      jsonInvitations.value.size shouldBe 1
      val jsonInvitation = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]
      (jsonInvitation \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (jsonInvitation \ "arn").as[String] shouldBe arn.value
      (jsonInvitation \ "clientType").asOpt[String] shouldBe None
      (jsonInvitation \ "status").as[String] shouldBe "Accepted"
      (jsonInvitation \ "clientActionUrl").asOpt[String] shouldBe None
    }

    "return 403 when arn is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn2, None, None, None, None, None, None)(request)

      status(response) shouldBe 403
      await(response) shouldBe NoPermissionOnAgency
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenClientMtdItId(mtdItId)

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitations(arn2, None, None, None, None, None, None)(request)

      status(response) shouldBe 401
    }
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {

    val request = FakeRequest("GET", "/agencies/:arn/invitations/sent")
    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)

    "return 200 with an invitation entity" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200

      val json = jsonBodyOf(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn.value
      (json \ "clientType").as[String] shouldBe "personal"
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe "FOO"
      (json \ "clientIdType").as[String] shouldBe "MTDITID"
      (json \ "suppliedClientId").as[String] shouldBe "FOO"
      (json \ "suppliedClientIdType").as[String] shouldBe "MTDITID"
      (json \ "clientActionUrl").as[String].matches("(\\/invitations\\/personal\\/[A-Z0-9]{8}\\/my-agency)") shouldBe true
    }

    "return 200 with Accepted VAT invitation without client type and skip providing action url" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          None,
          Service.Vat,
          clientIdentifierVat,
          clientIdentifierVat,
          DateTime.now(),
          LocalDate.now()))

      await(invitationsRepo.update(invitation, Accepted, DateTime.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200

      val json = jsonBodyOf(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "clientType").asOpt[String] shouldBe None
      (json \ "status").as[String] shouldBe "Accepted"
      (json \ "clientActionUrl").asOpt[String] shouldBe None
    }

    "return 200 with Accepted ITSA invitation without client type and skip providing action url" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val invitation = await(
        invitationsRepo.create(
          arn,
          None,
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      await(invitationsRepo.update(invitation, Accepted, DateTime.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200

      val json = jsonBodyOf(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "clientType").asOpt[String] shouldBe None
      (json \ "status").as[String] shouldBe "Accepted"
      (json \ "clientActionUrl").asOpt[String] shouldBe None
    }

    "return 403 when invitation is for another agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(Arn("TARN0000002"))

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 403
    }

    "return 403 when arn is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 403
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenClientMtdItId(mtdItId)

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 401
    }

    "return 404 when invitation does not exist" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.getSentInvitation(arn, InvitationId("A7GJRTMY4DS3T"))(request)

      status(response) shouldBe 404
    }
  }
}


