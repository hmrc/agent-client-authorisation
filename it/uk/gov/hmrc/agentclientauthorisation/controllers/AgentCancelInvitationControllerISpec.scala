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
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{InvitationNotFound, NoPermissionOnAgency}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestHalResponseInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentCancelInvitationControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
  }

  def createInvitation(arn: Arn,
                       testClient: TestClient[_],
                       hasEmail: Boolean = true): Future[Invitation] = {
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if(hasEmail) Some(dfe(testClient.clientName)) else None,
      DateTime.now(DateTimeZone.UTC),
      LocalDate.now().plusDays(14))
  }

  trait StubSetup {
    givenAuditConnector()
    givenAuthorisedAsAgent(arn)
    givenGetAgencyNameAgentStub
  }

  def runSuccessfulCancelledInvitation(testClient: TestClient[_]): Unit = {
    val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")
    val getResult = FakeRequest("GET", "agencies/:arn/invitations/sent/:invitationId")

    s"return 204 when an invitation is successfully cancelled for ${testClient.service}" in new StubSetup {

      val invitation: Invitation = await(createInvitation(arn, testClient))

      val response = controller.cancelInvitation(arn, invitation.invitationId)(request)
      status(response) shouldBe 204

      val updatedInvitation = controller.getSentInvitation(arn, invitation.invitationId)(getResult)
      status(updatedInvitation) shouldBe 200

      val invitationStatus = jsonBodyOf(updatedInvitation).as[TestHalResponseInvitation].status

      invitationStatus shouldBe Cancelled.toString
    }
  }

  def runUnsuccessfulCanceledInvitation(testClient: TestClient[_]): Unit = {
    s"return NoPermissionOnAgency when the logged in arn doesn't not match the invitation for ${testClient.service}" in {

      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")

      givenAuditConnector()
      givenAuthorisedAsAgent(arn2)
      givenGetAgencyNameAgentStub

      val invitation: Invitation = await(createInvitation(arn, testClient))

      val response = controller.cancelInvitation(arn2, invitation.invitationId)(request)

      await(response) shouldBe NoPermissionOnAgency
    }
  }

  "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {

    uiClients.foreach { client =>
      runSuccessfulCancelledInvitation(client)
      runUnsuccessfulCanceledInvitation(client)
    }

    "return InvitationNotFound when there is no invitation to cancel" in {
      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.cancelInvitation(arn, InvitationId("A7GJRTMY4DS3T"))(request)

      await(response) shouldBe InvitationNotFound
    }
  }
}


