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
import play.api.libs.json.JsObject
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class TransitionalAgentGetInvitationControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  override protected def additionalConfiguration: Map[String, Any] =
    super.additionalConfiguration + ("acr-mongo-activated" -> true)

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(invitationsRepo.ensureIndexes())
    ()
  }

  val nonExistentInvitationId = "A7GJRTMY4DS3T"
  val invitationId = "A7GJRTMY4DS9T"

  val invitationLinkRegex: String => String =
    (clientType: String) =>
      s"(http:\\/\\/localhost:9448\\/invitations\\/$clientType-taxes\\/manage-who-can-deal-with-HMRC-for-you\\/[A-Z0-9]{8}\\/my-agency)"

  private val testClients = List(itsaClient, irvClient, vatClient, trustClient, trustNTClient, cgtClient, pptClient, itsaSuppClient)

  def createExpectedInvitation(arn: Arn, testClient: TestClient[_]): Invitation =
    Invitation(
      invitationId = InvitationId(invitationId),
      arn = arn,
      clientType = testClient.clientType,
      service = testClient.service,
      clientId = testClient.clientId,
      suppliedClientId = testClient.suppliedClientId,
      expiryDate = LocalDate.now().plusDays(21),
      clientActionUrl = None,
      detailsForEmail = None,
      events = List(
        StatusChangeEvent(
          status = Pending,
          time = LocalDateTime.now()
        )
      ),
      fromAcr = true
    )

  def createInvitationInACA(arn: Arn, testClient: TestClient[_], hasEmail: Boolean = true): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if (hasEmail) Some(dfe(testClient.clientName)) else None,
      None
    )

  "GET /agencies/:arn/invitations/sent/:invitationId when transition flag is on" should {

    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)

    testClients.foreach { client =>
      runGetInvitationIdFromACRSpec(client)
      runGetInvitationIdFromACASpec(client)
    }

    "return 403 when invitation is for another agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(Arn("TARN0000002"))
      givenAcrInvitationNotFound(nonExistentInvitationId)
      val invitation = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, clientIdentifier, clientIdentifier, None, None)
      )

      val request =
        FakeRequest("GET", s"/agencies/$arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

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
        FakeRequest("GET", s"/agencies/$arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 401
    }

    "return 404 when invitation does not exist in ACR or ACA" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenAcrInvitationNotFound(nonExistentInvitationId)
      val request = FakeRequest("GET", s"/agencies/$arn/invitations/sent/$nonExistentInvitationId").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, InvitationId(nonExistentInvitationId))(request)

      status(response) shouldBe 404
    }
  }

  def runGetInvitationIdFromACRSpec[T <: TaxIdentifier](testClient: TestClient[T]): Unit =
    s"return 200 get Invitation stored in ACR for ${testClient.service}" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("my-agency"), Some("email"))
      stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("my-agency")))
      val invitation: Invitation = createExpectedInvitation(arn, testClient)
      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, testClient.clientName)
      val request =
        FakeRequest("GET", s"/agencies/$arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200

      val json = contentAsJson(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn.value
      (json \ "service").as[String] shouldBe testClient.service.id
      (json \ "clientType").asOpt[String] shouldBe invitation.clientType
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe invitation.clientId.value
      (json \ "clientIdType").as[String] shouldBe invitation.clientId.typeId
      (json \ "suppliedClientId").as[String] shouldBe invitation.suppliedClientId.value
      (json \ "suppliedClientIdType").as[String] shouldBe invitation.suppliedClientId.typeId
    }

  def runGetInvitationIdFromACASpec[T <: TaxIdentifier](testClient: TestClient[T]): Unit =
    s"return 200 get Invitation not stored in ACR but found in ACA for ${testClient.service}" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenGetAgencyDetailsStub(arn, Some("my-agency"), Some("email"))
      stubFetchOrCreateAgentReference(arn, AgentReferenceRecord("ABCDEFGH", arn, Seq("my-agency")))
      val invitation: Invitation = await(createInvitationInACA(arn, testClient))
      givenAcrInvitationNotFound(invitation.invitationId.value)

      val request =
        FakeRequest("GET", s"/agencies/$arn/invitations/sent/${invitation.invitationId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val response = controller.getSentInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 200

      val json = contentAsJson(response).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn.value
      (json \ "service").as[String] shouldBe testClient.service.id
      (json \ "clientType").asOpt[String] shouldBe invitation.clientType
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe invitation.clientId.value
      (json \ "clientIdType").as[String] shouldBe invitation.clientId.typeId
      (json \ "suppliedClientId").as[String] shouldBe invitation.suppliedClientId.value
      (json \ "suppliedClientIdType").as[String] shouldBe invitation.suppliedClientId.typeId
    }
}
