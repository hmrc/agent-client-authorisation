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
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.InvitationNotFound
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentSetRelationshipEndedControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
    ()
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
      LocalDate.now().plusDays(21),
      None)
  }

  trait StubSetup {
    givenAuditConnector()
    givenAuthorisedAsAgent(arn)
    givenGetAgencyDetailsStub(arn)
  }

  def runSuccessfulSetRelationshipEndedWithInvitationId[T<:TaxIdentifier](testClient: TestClient[T]): Unit = {
    val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/relationship-ended")

    s"return 204 when an ${testClient.service} invitation is successfully updated to isRelationshipEnded flag = true" in new StubSetup {

      val invitation: Invitation = await(createInvitation(arn, testClient))

      val response = controller.setRelationshipEndedForInvitation(invitation.invitationId, "Client")(request)

      status(response) shouldBe 204

      val result = await(invitationsRepo.findByInvitationId(invitation.invitationId))
      result.get.status shouldBe DeAuthorised
    }
  }

  def runSuccessfulSetRelationshipEnded[T<:TaxIdentifier](testClient: TestClient[T]): Unit = {
    val request = FakeRequest("PUT", "/invitations/set-relationship-ended")

    s"return 204 when an ${testClient.service} invitation is successfully updated to isRelationshipEnded flag = true" in new StubSetup {

      val invitation: Invitation = await(createInvitation(arn, testClient))
      await(invitationsRepo.update(invitation, Accepted, DateTime.now()))

      val payload = SetRelationshipEndedPayload(arn, testClient.clientId.value, testClient.service.id, None)

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.toJson(payload)))

      status(response) shouldBe 204

      val result = await(invitationsRepo.findByInvitationId(invitation.invitationId))
      result.get.status shouldBe DeAuthorised
      result.get.isRelationshipEnded shouldBe true
      result.get.relationshipEndedBy shouldBe Some("HMRC")
    }
  }

  "PUT /invitations/:invitationId/relationship-ended" should {

    uiClients.foreach { client =>
      runSuccessfulSetRelationshipEndedWithInvitationId(client)
    }

    "return InvitationNotFound when there is no invitation to setRelationshipEnded" in {
      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/relationship-ended")
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.setRelationshipEndedForInvitation(InvitationId("A7GJRTMY4DS3T"), "Agent")(request)

      await(response) shouldBe InvitationNotFound
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
      await(invitationsRepo.update(invitation, PartialAuth, DateTime.now()))

      val payload = SetRelationshipEndedPayload(arn, altItsaClient.clientId.value, altItsaClient.service.id, Some("Client"))

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.toJson(payload)))

      status(response) shouldBe NO_CONTENT

      val result = await(invitationsRepo.findByInvitationId(invitation.invitationId))

      result.get.status shouldBe DeAuthorised
      result.get.isRelationshipEnded shouldBe true
      result.get.relationshipEndedBy shouldBe Some("Client")
    }

    "return status 404 when invitation not found" in {
      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(
        s"""{
          |"arn": "${arn.value}",
          |"clientId": "AB123456A",
          |"service": "HMRC-MTD-IT"
          |}""".stripMargin)))

      await(response) shouldBe InvitationNotFound
    }

    "return status 400 Bad Request when body empty" in {
      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(
        s"""{}""".stripMargin)))

      status(response) shouldBe 400
    }

    "return status 400 Bad Request when body contains wrong fields" in {
      val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
      givenAuditConnector()

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(
        s"""{"invalid": "value"}""".stripMargin)))

      status(response) shouldBe 400
    }
  }
}


