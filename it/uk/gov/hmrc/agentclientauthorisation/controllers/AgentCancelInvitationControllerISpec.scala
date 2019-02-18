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
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{InvitationNotFound, NoPermissionOnAgency}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId

import scala.concurrent.ExecutionContext.Implicits.global

class AgentCancelInvitationControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepository])

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
  }

  "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {

    val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")
    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)

    "return 204 when an invitation is successfully cancelled" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val invitation = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response = controller.cancelInvitation(arn, invitation.invitationId)(request)

      status(response) shouldBe 204
    }

    "return InvitationNotFound when there is no invitation to cancel" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response = controller.cancelInvitation(arn, InvitationId("A7GJRTMY4DS3T"))(request)

      await(response) shouldBe InvitationNotFound
    }

    "return NoPermissionOnAgency when the logged in arn doesn't not match the invitation" in {
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

      val response = controller.cancelInvitation(arn2, invitation.invitationId)(request)

      await(response) shouldBe NoPermissionOnAgency
    }
  }
}


