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

import org.apache.pekko.stream.Materializer
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsArray, JsObject, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestHalResponseInvitations
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.{Instant, LocalDate, LocalDateTime}
import scala.concurrent.Future

class AgentGetInvitationControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
  }

  val beyond30Days: LocalDateTime = LocalDateTime.now().minusDays(31L)

  val invitationLinkRegex: String => String =
    (clientType: String) =>
      s"(http:\\/\\/localhost:9448\\/invitations\\/$clientType-taxes\\/manage-who-can-deal-with-HMRC-for-you\\/[A-Z0-9]{8}\\/my-agency)"

  val testClients = List(itsaClient, irvClient, vatClient, trustClient, trustNTClient, cgtClient, pptClient, itsaSuppClient)
  val olderThan30DaysClient1 = itsaClient.copy(suppliedClientId = mtdItId2)
  val olderThan30DaysClient2 = vatClient.copy(suppliedClientId = vrn2)
  val olderThan30Days = List(olderThan30DaysClient1, olderThan30DaysClient2)

  // Prior to Client Type and Client ActionUrl
  val legacyList = List(itsaClient, irvClient, vatClient)

  def createInvitation(arn: Arn, testClient: TestClient[_], startDate: LocalDateTime = LocalDateTime.now()): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      Some(dfe(testClient.clientName)),
      startDate,
      LocalDate.now().plusDays(21),
      None
    )

  trait TestSetup extends BaseISpec {
    testClients.foreach(client => await(createInvitation(arn, client)))
    olderThan30Days.foreach(client => await(createInvitation(arn, client, beyond30Days)))
    givenAuditConnector()
    givenAuthorisedAsAgent(arn)
    givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
    stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("name")))
  }

  "GET /agencies/:arn/invitations/sent" when {
    val request = FakeRequest("GET", "/agencies/:arn/invitations/sent").withHeaders("Authorization" -> "Bearer testtoken")

    "the ACR mongo feature switch is enabled" should {

      lazy val appWithAcrMongoEnabled: GuiceApplicationBuilder =
        new GuiceApplicationBuilder().configure(additionalConfiguration + ("acr-mongo-activated" -> true))
      lazy val controllerWithACR = appWithAcrMongoEnabled.injector().instanceOf[AgencyInvitationsController]

      def acrJson(service: String, idType: String, status: String) = Json.obj(
        "invitationId"         -> "123",
        "arn"                  -> arn.value,
        "service"              -> service,
        "clientId"             -> "123456789",
        "clientIdType"         -> idType,
        "suppliedClientId"     -> "234567890",
        "suppliedClientIdType" -> idType,
        "clientName"           -> "Macrosoft",
        "status"               -> status,
        "relationshipEndedBy"  -> "Me",
        "clientType"           -> "personal",
        "expiryDate"           -> "2020-01-01",
        "created"              -> Instant.now(),
        "lastUpdated"          -> Instant.now()
      )

      "return Invitations for Agent with ARN Query Params" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCMTDVAT, VrnType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val response: Future[Result] = controllerWithACR.getSentInvitations(arn, None, None, None, None, None, None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 9
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return Invitations for Agent with ARN and Service Query Params" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}&services=${Service.HMRCMTDIT}&services=${Service.HMRCTERSORG}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCTERSORG, UtrType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val serviceOptions: Option[String] = Some(s"${Service.HMRCMTDIT},${Service.HMRCTERSORG}")
        val response: Future[Result] = controllerWithACR.getSentInvitations(arn, None, serviceOptions, None, None, None, None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 3
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return Invitations for Agent with ARN and ClientId Query Params" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}&clientIds=${mtdItId.value}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCMTDIT, MtdItIdType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val response: Future[Result] = controllerWithACR.getSentInvitations(arn, None, None, None, Some(mtdItId.value), None, None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 3
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return Invitations for Agent with ARN and Status Query Params" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}&status=${Pending.toString}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCMTDIT, MtdItIdType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val response: Future[Result] = controllerWithACR.getSentInvitations(arn, None, None, None, None, Some(Pending), None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 9
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return Invitations for Agent with ARN, Status, ClientId and Service Query Params" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}&services=${Service.HMRCMTDIT}&clientIds=${mtdItId.value}&status=${Pending.toString}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCMTDIT, MtdItIdType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val response: Future[Result] =
          controllerWithACR.getSentInvitations(arn, None, Some(Service.HMRCMTDIT), None, Some(mtdItId.value), Some(Pending), None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 2
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return Invitations for Agent with ARN, Status, ClientId and Service Query Params for SUPP" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}&services=${Service.HMRCMTDITSUPP}&clientIds=${mtdItId.value}&status=${Pending.toString}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCMTDITSUPP, MtdItIdType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val response: Future[Result] =
          controllerWithACR.getSentInvitations(arn, None, Some(s"${Service.HMRCMTDITSUPP}"), None, Some(mtdItId.value), Some(Pending), None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 2
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return Invitations while ignoring the custom date range query param" in new TestSetup {

        val expectedQueryParams = s"?arn=${arn.value}"
        val acrResponseBody: JsArray = Json.arr(acrJson(Service.HMRCMTDVAT, VrnType.id, Pending.toString))
        stubLookupInvitations(expectedQueryParams, OK, acrResponseBody)

        val response: Future[Result] =
          controllerWithACR.getSentInvitations(arn, None, None, None, None, None, Some(LocalDate.now().plusDays(1L)))(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 9
        json.invitations.forall(inv => LocalDateTime.parse(inv.created).isAfter(beyond30Days)) shouldBe true
      }

      "return no Invitations for Agent" in {
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("name")))

        val expectedQueryParams = s"?arn=${arn.value}"
        stubLookupInvitations(expectedQueryParams, NOT_FOUND)

        val response = controllerWithACR.getSentInvitations(arn, None, None, None, None, None, None)(request)

        status(response) shouldBe 200

        val jsonResponse = contentAsJson(response).as[JsObject]
        val json = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 0
      }
    }

    "the ACR mongo feature switch is disabled" should {

      "return Invitations for Agent with ARN Query Params" in new TestSetup {

        val response: Future[Result] = controller.getSentInvitations(arn, None, None, None, None, None, None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 10
      }

      "return Invitations for Agent with ARN and Service Query Params" in new TestSetup {

        val serviceOptions: Option[String] = Some(s"${Service.HMRCMTDIT},${Service.HMRCTERSORG}")
        val response: Future[Result] = controller.getSentInvitations(arn, None, serviceOptions, None, None, None, None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 3
      }

      "return Invitations for Agent with ARN and ClientId Query Params" in new TestSetup {

        val response: Future[Result] = controller.getSentInvitations(arn, None, None, None, Some(mtdItId.value), None, None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 3
      }

      "return Invitations for Agent with ARN and Status Query Params" in new TestSetup {

        val response: Future[Result] = controller.getSentInvitations(arn, None, None, None, None, Some(Pending), None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 10
      }

      "return Invitations for Agent with ARN and CreatedBefore Query Params" in new TestSetup {

        val response: Future[Result] = controller.getSentInvitations(arn, None, None, None, None, None, Some(LocalDate.now().minusDays(30)))(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]

        json.invitations.length shouldBe 8
      }

      "return Invitations for Agent with ARN, Services and CreatedBefore Query Params" in new TestSetup {

        val serviceOptions: Option[String] = Some(s"${Service.HMRCMTDIT},${Service.HMRCTERSORG}")
        val response: Future[Result] =
          controller.getSentInvitations(arn, None, serviceOptions, None, None, None, Some(LocalDate.now().minusDays(30)))(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
        json.invitations.length shouldBe 2
      }

      "return Invitations for Agent with ARN, Status, ClientId and Service Query Params" in new TestSetup {

        val response: Future[Result] =
          controller.getSentInvitations(arn, None, Some(s"${Service.HMRCMTDIT}"), None, Some(mtdItId.value), Some(Pending), None)(request)

        status(response) shouldBe 200
      }

      "return Invitations for Agent with ARN, Status, ClientId and Service Query Params for SUPP" in new TestSetup {

        val response: Future[Result] =
          controller.getSentInvitations(arn, None, Some(s"${Service.HMRCMTDITSUPP}"), None, Some(mtdItId.value), Some(Pending), None)(request)

        status(response) shouldBe 200

        val jsonResponse: JsObject = contentAsJson(response).as[JsObject]
        val json: TestHalResponseInvitations = (jsonResponse \ "_embedded").as[TestHalResponseInvitations]
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
          .create(arn, Some("personal"), Service.MtdIt, clientIdentifier, clientIdentifier, None, LocalDateTime.now(), LocalDate.now(), None)
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
          .create(arn, Some("personal"), Service.MtdIt, clientIdentifier, clientIdentifier, None, LocalDateTime.now(), LocalDate.now(), None)
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
