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

import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.InvitationNotFound
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.LocalDateTime
import scala.concurrent.Future

class AgentSetRelationshipEndedControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

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

  def runSuccessfulSetRelationshipEnded[T <: TaxIdentifier](testClient: TestClient[T]): Unit = {
    val request = FakeRequest("PUT", "/invitations/set-relationship-ended")

    s"return 204 when an ${testClient.service} invitation is successfully updated to isRelationshipEnded flag = true" in new StubSetup {

      val invitation: Invitation = await(createInvitation(arn, testClient))
      await(invitationsRepo.update(invitation, Accepted, LocalDateTime.now()))

      val payload = SetRelationshipEndedPayload(arn, testClient.clientId.value, testClient.service.id, Some("HMRC"))

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.toJson(payload)))

      status(response) shouldBe 204

      val result = await(invitationsRepo.findByInvitationId(invitation.invitationId))
      result.get.status shouldBe DeAuthorised
      result.get.isRelationshipEnded shouldBe true
      result.get.relationshipEndedBy shouldBe Some("HMRC")

      verifyACRChangeStatusWasNOTSent(arn, testClient.service.id, testClient.clientId.value)
    }
  }

  "PUT /invitations/set-relationship-ended" should {

    uiClients.foreach { client =>
      runSuccessfulSetRelationshipEnded(client)
    }

    "update status to Deauthorised for Partialauth" in {

      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val invitation: Invitation = await(createInvitation(arn, altItsaClient))
      await(invitationsRepo.update(invitation, PartialAuth, LocalDateTime.now()))

      val payload = SetRelationshipEndedPayload(arn, altItsaClient.clientId.value, altItsaClient.service.id, Some("Client"))

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.toJson(payload)))

      status(response) shouldBe NO_CONTENT

      val result = await(invitationsRepo.findByInvitationId(invitation.invitationId))

      result.get.status shouldBe DeAuthorised
      result.get.isRelationshipEnded shouldBe true
      result.get.relationshipEndedBy shouldBe Some("Client")
    }

    "update the most recent invitation when there is more than 1 because some previous update failed" in {

      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val olderInvitation: Invitation = await(createInvitation(arn, altItsaClient))
      await(invitationsRepo.update(olderInvitation, PartialAuth, LocalDateTime.now().minusSeconds(300)))

      val newerInvitation: Invitation = await(createInvitation(arn, altItsaClient))
      await(invitationsRepo.update(newerInvitation, PartialAuth, LocalDateTime.now()))

      val payload = SetRelationshipEndedPayload(arn, altItsaClient.clientId.value, altItsaClient.service.id, Some("Agent"))

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.toJson(payload)))

      status(response) shouldBe NO_CONTENT

      val olderInvitationResult = await(invitationsRepo.findByInvitationId(olderInvitation.invitationId))

      olderInvitationResult.get.status shouldBe PartialAuth
      olderInvitationResult.get.isRelationshipEnded shouldBe false
      olderInvitationResult.get.relationshipEndedBy shouldBe None

      val newerInvitationResult = await(invitationsRepo.findByInvitationId(newerInvitation.invitationId))

      newerInvitationResult.get.status shouldBe DeAuthorised
      newerInvitationResult.get.isRelationshipEnded shouldBe true
      newerInvitationResult.get.relationshipEndedBy shouldBe Some("Agent")
    }

    "return status 404 when invitation not found" in {
      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(s"""{
                                                                                          |"arn": "${arn.value}",
                                                                                          |"clientId": "AB123456A",
                                                                                          |"service": "HMRC-MTD-IT"
                                                                                          |}""".stripMargin)))

      await(response) shouldBe InvitationNotFound
    }

    "return status 400 Bad Request when body empty" in {
      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(s"""{}""".stripMargin)))

      status(response) shouldBe 400
    }

    "return status 400 Bad Request when body contains wrong fields" in {
      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(s"""{"invalid": "value"}""".stripMargin)))

      status(response) shouldBe 400
    }
  }
}
