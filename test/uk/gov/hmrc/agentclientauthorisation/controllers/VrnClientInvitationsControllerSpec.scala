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
import javax.inject.Provider
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, Vrn}
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.Generator

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class VrnClientInvitationsControllerSpec
    extends AkkaMaterializerSpec with ResettingMockitoSugar with ClientEndpointBehaviours with TestData {
  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }

  val controller =
    new VatClientInvitationsController(invitationsService)(metrics, microserviceAuthConnector, auditService, ecp) {
      override val authConnector: PlayAuthConnector = mockPlayAuthConnector
    }

  val invitationDbId: String = BSONObjectID.generate.stringify
  val invitationId: InvitationId = InvitationId("CBBBBBBBBBBCC")
  val generator = new Generator()

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  "getInvitation" should {
    "Return NoPermissionOnClient when given vrn does not match authVrn" in {
      clientAuthStub(clientVrnEnrolments)

      val response = await(controller.getInvitation(Vrn("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }
  }
}
