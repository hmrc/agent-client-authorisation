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

import play.api.libs.json.JsObject
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestHalResponseInvitations
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class AgentGetInvitationControllerISpec extends BaseISpec {

  override protected def additionalConfiguration: Map[String, Any] =
    super.additionalConfiguration + ("acr-mongo-activated" -> true)

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
    ()
  }

  val clientIdentifierVat = ClientIdentifier("101747696", VrnType.id)

  val invitationLinkRegex: String => String =
    (clientType: String) =>
      s"(http:\\/\\/localhost:9448\\/invitations\\/$clientType-taxes\\/manage-who-can-deal-with-HMRC-for-you\\/[A-Z0-9]{8}\\/my-agency)"

  val testClients = List(itsaClient, irvClient, vatClient, trustClient, trustNTClient, cgtClient, pptClient, itsaSuppClient)

  // Prior to Client Type and Client ActionUrl
  val legacyList = List(itsaClient, irvClient, vatClient)

  def createInvitation(arn: Arn, testClient: TestClient[_], hasEmail: Boolean = true): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      Some(dfe(testClient.clientName)),
      None
    )

  trait TestSetup {
    testClients.foreach(client => await(createInvitation(arn, client)))
    givenAuditConnector()
    givenAuthorisedAsAgent(arn)
    givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
    stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("name")))
  }

  "GET /agencies/:arn/invitations/sent" should {
    val request = FakeRequest("GET", "/agencies/:arn/invitations/sent").withHeaders("Authorization" -> "Bearer testtoken")

    "return Invitations for Agent without Query Params" in new TestSetup {

      val response = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 8
    }

    "return Invitations for Agent with Service Query Params" in new TestSetup {

      val serviceOptions = Some(s"${Service.HMRCMTDIT},${Service.HMRCTERSORG}")
      val response = controller.getSentInvitations(arn, None, serviceOptions, None, None, None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    "return Invitations for Agent with ClientId Query Params" in new TestSetup {

      val response = controller.getSentInvitations(arn, None, None, None, Some(mtdItId.value), None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    "return Invitations for Agent with Status Query Params" in new TestSetup {

      val response = controller.getSentInvitations(arn, None, None, None, None, Some(Pending), None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 8
    }

    "return Invitations for Agent with CreatedBefore Query Params" in new TestSetup {

      val response = controller.getSentInvitations(arn, None, None, None, None, None, Some(LocalDate.now().minusDays(30)))(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]

      json.invitations.length shouldBe 8
    }

    "return Invitations for Agent with Services and CreatedBefore Query Params" in new TestSetup {

      val serviceOptions = Some(s"${Service.HMRCMTDIT},${Service.HMRCTERSORG}")
      val response = controller.getSentInvitations(arn, None, serviceOptions, None, None, None, Some(LocalDate.now().minusDays(30)))(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    "return Invitations for Agent with Status, ClientId and Service Query Params" in new TestSetup {

      val response = controller.getSentInvitations(arn, None, Some(s"${Service.HMRCMTDIT}"), None, Some(mtdItId.value), Some(Pending), None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 1
    }

    "return Invitations for Agent with Status, ClientId and Service Query Params for SUPP" in new TestSetup {

      val response =
        controller.getSentInvitations(arn, None, Some(s"${Service.HMRCMTDITSUPP}"), None, Some(mtdItId.value), Some(Pending), None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
      val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 1
    }

    "return no Invitations for Agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("name")))

      val response = controller.getSentInvitations(arn, None, None, None, Some(mtdItId.value), None, None)(request)

      status(response) shouldBe 200

      val jsonResponse = contentAsJson(response).as[JsObject]
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

    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)

    testClients.foreach { client =>
      runGetInvitationIdSpec(client)
    }

    legacyList.foreach { client =>
      runGetInvitationLegacy(client)
    }

    "return 403 when invitation is for another agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(Arn("TARN0000002"))

      val invitation = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, clientIdentifier, clientIdentifier, None, None)
      )

      val request =
        FakeRequest("GET", s"/agencies/:arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 403
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenClientMtdItId(mtdItId)

      val invitation = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, clientIdentifier, clientIdentifier, None, None)
      )

      val request =
        FakeRequest("GET", s"/agencies/:arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 401
    }

    "return 404 when invitation does not exist" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val request = FakeRequest("GET", s"/agencies/:arn/invitations/sent/A7GJRTMY4DS3T").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, InvitationId("A7GJRTMY4DS3T"))(request)

      status(response) shouldBe 404
    }
  }

  def runGetInvitationIdSpec[T <: TaxIdentifier](testClient: TestClient[T]): Unit =
    s"return 200 get Invitation for ${testClient.service}" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("my-agency"), Some("email"))
      stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("my-agency")))

      val invitation: Invitation = await(createInvitation(arn, testClient))
      val request =
        FakeRequest("GET", s"/agencies/:arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200

      val json = contentAsJson(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn.value
      (json \ "clientType").asOpt[String] shouldBe invitation.clientType
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe invitation.clientId.value
      (json \ "clientIdType").as[String] shouldBe invitation.clientId.typeId
      (json \ "suppliedClientId").as[String] shouldBe invitation.suppliedClientId.value
      (json \ "suppliedClientIdType").as[String] shouldBe invitation.suppliedClientId.typeId
      (json \ "clientActionUrl").as[String].matches(invitationLinkRegex(invitation.clientType.get)) shouldBe true
      (json \ "detailsForEmail").asOpt[DetailsForEmail] shouldBe Some(dfe(testClient.clientName))
    }

  def runGetInvitationLegacy[T <: TaxIdentifier](testClient: TestClient[T]): Unit =
    s"return 200 get invitation Accepted for ${testClient.service} for no clientType and Skip Add ClientActionUrl" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("name")))

      val invitation: Invitation = await(createInvitation(arn, testClient.copy[T](clientType = None)))
      val request =
        FakeRequest("GET", s"/agencies/:arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      await(invitationsRepo.update(invitation, Accepted, LocalDateTime.now()))

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200
      val json = contentAsJson(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "clientType").asOpt[String] shouldBe None
      (json \ "status").as[String] shouldBe "Accepted"
      (json \ "clientActionUrl").asOpt[String] shouldBe None
    }
}
