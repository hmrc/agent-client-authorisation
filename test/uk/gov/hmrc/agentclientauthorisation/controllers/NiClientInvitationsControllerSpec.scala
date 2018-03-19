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

import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.mvc.Results.Ok
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.ninoSpace
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{ Enrolments, PlayAuthConnector }
import uk.gov.hmrc.domain.{ Generator, Nino }

import scala.concurrent.{ ExecutionContext, Future }

class NiClientInvitationsControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with ClientEndpointBehaviours with TestData {

  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val mockHalResource: ClientInvitationsHal = resettingMock[ClientInvitationsHal]

  val controller = new NiClientInvitationsController(invitationsService)(metrics, microserviceAuthConnector, auditService) {
    override val authConnector: PlayAuthConnector = mockPlayAuthConnector
  }

  val invitationDbId: String = BSONObjectID.generate.stringify
  val invitationId: InvitationId = InvitationId("BT6NLAYD6FSYU")
  val generator = new Generator()

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any[ExecutionContext])).thenReturn(returnValue)

  "getInvitation" should {
    "Return OK when given nino has spaces in between" in {
      clientAuthStub(clientNiEnrolments)
      val invitation = anInvitation(ninoSpace)
        .copy(service = Service.PersonalIncomeRecord, clientId = ninoSpace, postcode = None)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))

      val response = await(controller
        .getInvitation(Nino("AA000003D"), invitationId)(FakeRequest()))

      status(response) shouldBe status(Ok)
    }

    "Return NO_CONTENT when accepting an invitation that has nino with spaces in between" in {
      clientAuthStub(clientNiEnrolments)
      val invitation = anInvitation(ninoSpace)
        .copy(service = Service.PersonalIncomeRecord, clientId = ninoSpace, postcode = None)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsAccepted thenReturn (Future successful Right(transitionInvitation(invitation, Accepted)))

      val response = await(controller
        .acceptInvitation(Nino("AA000003D"), invitationId)(FakeRequest()))

      response.header.status shouldBe 204
      verifyAgentClientRelationshipCreatedAuditEvent()
    }

    "Return NO_CONTENT when rejecting an invitation that has nino with spaces in between" in {
      clientAuthStub(clientNiEnrolments)
      val invitation = anInvitation(ninoSpace)
        .copy(service = Service.PersonalIncomeRecord, clientId = ninoSpace, postcode = None)
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))
      whenInvitationIsRejected thenReturn (Future successful Right(transitionInvitation(invitation, Rejected)))

      val response = await(controller
        .rejectInvitation(Nino("AA000003D"), invitationId)(FakeRequest()))

      response.header.status shouldBe 204
    }
  }

}
