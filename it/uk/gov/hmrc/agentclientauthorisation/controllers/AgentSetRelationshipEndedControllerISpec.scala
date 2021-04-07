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

  def runSuccessfulSetRelationshipEnded[T<:TaxIdentifier](testClient: TestClient[T]): Unit = {
    val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/relationship-ended")

    s"return 204 when an ${testClient.service} invitation is successfully updated to isRelationshipEnded flag = true" in new StubSetup {

      val invitation: Invitation = await(createInvitation(arn, testClient))

      val response = controller.setRelationshipEnded(invitation.invitationId, "Client")(request)

      status(response) shouldBe 204

      val result = await(invitationsRepo.findByInvitationId(invitation.invitationId))
      result.get.status shouldBe DeAuthorised
    }
  }

  "PUT /invitations/:invitationId/relationship-ended" should {

    uiClients.foreach { client =>
      runSuccessfulSetRelationshipEnded(client)
    }

    "return InvitationNotFound when there is no invitation to setRelationshipEnded" in {
      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/relationship-ended")
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.setRelationshipEnded(InvitationId("A7GJRTMY4DS3T"), "Agent")(request)

      await(response) shouldBe InvitationNotFound
    }
  }
}


