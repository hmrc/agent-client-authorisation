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
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.json.{JsArray, JsObject}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestHalResponseInvitations
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentGetInvitationControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
    await(dropMongoDb())
  }

  val clientIdentifierVat = ClientIdentifier("101747696", VrnType.id)

  val invitationLinkRegex: String => String =
    (clientType: String) => s"(http:\\/\\/localhost:9448\\/invitations\\/${clientType}\\/[A-Z0-9]{8}\\/my-agency)"

  case class TestClient(
                         clientType: Option[String],
                         service: Service,
                         urlIdentifier: String,
                         clientId: TaxIdentifier,
                         suppliedClientId: TaxIdentifier)

  val itsaClient = TestClient(personal, Service.MtdIt, "MTDITID", mtdItId, nino)
  val irvClient = TestClient(personal, Service.PersonalIncomeRecord, "NI", nino, nino)
  val vatClient = TestClient(personal, Service.Vat, "VRN", vrn, vrn)
  val trustClient = TestClient(business, Service.Trust, "UTR", utr, utr)

  val testClients = List(itsaClient, irvClient, vatClient, trustClient)

  def createInvitation(clientType: Option[String],
                       service: Service,
                       arn: Arn,
                       clientId: ClientId,
                       suppliedClientId: ClientId): Future[Invitation] = {
    invitationsRepo.create(
      arn,
      clientType,
      service,
      clientId,
      suppliedClientId,
      Some(dfe),
      DateTime.now(DateTimeZone.UTC),
      LocalDate.now().plusDays(14))
  }

  "GET /agencies/:arn/invitations/sent" should {
    val request = FakeRequest("GET", "/agencies/:arn/invitations/sent")

    "return Invitations for Agent without Query Params" in {
      testClients.foreach(client => createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      println(s"So $json")
      json.invitations.length shouldBe 4
    }

    "return Invitations for Agent with Service Query Params" in {
      testClients.foreach(client => createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val serviceOptions = Some(s"${Service.HMRCMTDIT},${Service.HMRCTERSORG}")
      val response = controller.getSentInvitations(arn, None, serviceOptions, None, None, None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    "return Invitations for Agent with ClientId Query Params" in {
      testClients.foreach(client => createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val response = controller.getSentInvitations(arn, None, None, None, Some(mtdItId.value), None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 1
    }

    "return Invitations for Agent with Status Query Params" in {
      testClients.foreach(client => createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val response = controller.getSentInvitations(arn, None, None, None, None, Some(Pending), None)(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 4
    }

    "return Invitations for Agent with CreatedBefore Query Params" in {
      testClients.foreach(client => createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyNameAgentStub

      val response = controller.getSentInvitations(arn, None, None, None, None, None, Some(LocalDate.now().minusDays(30)))(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 4
    }

    "return no Invitations for Agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.getSentInvitations(arn, None, None, None, Some(mtdItId.value), None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = jsonBodyOf(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 0
    }

    "return 403 when it is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.getSentInvitations(arn2, None, None, None, None, None, None)(request)

      status(response) shouldBe 403
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenClientMtdItId(mtdItId)

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

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
          None,
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
      (json \ "clientActionUrl").as[String].matches(invitationLinkRegex("personal")) shouldBe true
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
          None,
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
          None,
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
          None,
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
          None,
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
          None,
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


