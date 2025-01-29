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
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{InvitationNotFound, NoPermissionOnAgency}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{PlatformAnalyticsStubs, TestHalResponseInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatusAction
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatusAction.unapply

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class AgentCancelInvitationWithACRControllerISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  override protected def additionalConfiguration: Map[String, Any] =
    super.additionalConfiguration + ("acr-mongo-activated" -> true)

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
    ()
  }

  def createInvitation(arn: Arn, testClient: TestClient[_], hasEmail: Boolean = true): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if (hasEmail) Some(dfe(testClient.clientName)) else None,
      None
    )

  trait StubSetup {
    givenAuditConnector()
    givenAuthorisedAsAgent(arn)
    givenGetAgencyDetailsStub(arn)
  }

  def runSuccessfulCancelledInvitation[T <: TaxIdentifier](testClient: TestClient[T]): Unit = {
    val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")
      .withHeaders("X-Session-ID" -> "1234")
      .withHeaders("Authorization" -> "Bearer testtoken")
    val getResult = FakeRequest("GET", "agencies/:arn/invitations/sent/:invitationId").withHeaders("Authorization" -> "Bearer testtoken")

    s"return 204 when an ${testClient.service} invitation is successfully cancelled for ${testClient.clientId.value} in ACA" in new StubSetup {

      val invitation: Invitation = await(createInvitation(arn, testClient))

      givenACRChangeStatusByIdNotFound(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Cancel))

      givenPlatformAnalyticsRequestSent(true)

      val response = controller.cancelInvitation(arn, invitation.invitationId)(request)
      status(response) shouldBe 204

      val updatedInvitation = controller.getSentInvitation(arn, invitation.invitationId)(getResult)
      status(updatedInvitation) shouldBe 200

      val invitationStatus = contentAsJson(updatedInvitation).as[TestHalResponseInvitation].status

      invitationStatus shouldBe Cancelled.toString

      val event = Event(
        "authorisation request",
        "cancelled",
        testClient.service.id.toLowerCase,
        Seq(
          DimensionValue(7, testClient.clientType.getOrElse("personal")),
          DimensionValue(8, invitation.invitationId.value),
          DimensionValue(9, "unknown")
        ) ++ invitation.altItsa.map(v => Seq(DimensionValue(11, v.toString))).getOrElse(Seq.empty)
      )

      verifySingleEventAnalyticsRequestSent(List(event))
      verifyACRChangeStatusByIdSent(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Cancel))
    }

    s"return 204 when an ${testClient.service} invitation is successfully cancelled for ${testClient.clientId.value} in ACR" in new StubSetup {

      val invitationId: InvitationId = InvitationId("AnyInvitationId")
      givenACRChangeStatusByIdSuccess(invitationId = invitationId.value, action = unapply(InvitationStatusAction.Cancel))
      givenPlatformAnalyticsRequestSent(true)

      val response: Future[Result] = controller.cancelInvitation(arn, invitationId)(request)
      status(response) shouldBe 204

      verifyACRChangeStatusByIdSent(invitationId = invitationId.value, action = unapply(InvitationStatusAction.Cancel))
    }
  }

  def runUnsuccessfulCanceledInvitation[T <: TaxIdentifier](testClient: TestClient[T]): Unit = {
    s"return NoPermissionOnAgency when the logged in arn doesn't not match the invitation for ${testClient.service} to client: ${testClient.clientId.value}" in {

      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel").withHeaders("Authorization" -> "Bearer testtoken")

      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)
      givenGetAgencyDetailsStub(arn2)
      val invitation: Invitation = await(createInvitation(arn, testClient))

      givenACRChangeStatusByIdNotFound(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Cancel))

      val response = controller.cancelInvitation(arn2, invitation.invitationId)(request)

      await(response) shouldBe NoPermissionOnAgency
      verifyACRChangeStatusByIdSent(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Cancel))
    }
    s"return ACR 400 for ${testClient.service} to client: ${testClient.clientId.value}" in {

      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel").withHeaders("Authorization" -> "Bearer testtoken")

      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)
      givenGetAgencyDetailsStub(arn2)
      val invitation: Invitation = await(createInvitation(arn, testClient))

      givenACRChangeStatusByIdBadRequest(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Cancel))

      val response = controller.cancelInvitation(arn2, invitation.invitationId)(request)

      status(response) shouldBe 400
      verifyACRChangeStatusByIdSent(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Cancel))
    }
  }

  "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {

    uiClients.foreach { client =>
      runSuccessfulCancelledInvitation(client)
      runUnsuccessfulCanceledInvitation(client)
    }

    "return InvitationNotFound when there is no invitation to cancel in ACA and ACR" in {
      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel").withHeaders("Authorization" -> "Bearer testtoken")
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      val invitationId = InvitationId("A7GJRTMY4DS3T")
      givenACRChangeStatusByIdNotFound(invitationId = invitationId.value, action = unapply(InvitationStatusAction.Cancel))

      val response = controller.cancelInvitation(arn, invitationId)(request)

      await(response) shouldBe InvitationNotFound

      verifyACRChangeStatusByIdSent(invitationId = invitationId.value, action = unapply(InvitationStatusAction.Cancel))
    }
  }
}
