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
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito._
import play.api.libs.json.JsArray
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.StatusUpdateFailure
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, MtdItId, Vrn}
import uk.gov.hmrc.auth.core.retrieve.Retrieval
import uk.gov.hmrc.auth.core.{Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Generator, Nino}

import scala.concurrent.{ExecutionContext, Future}

class VrnClientInvitationsControllerSpec
    extends AkkaMaterializerSpec with ResettingMockitoSugar with ClientEndpointBehaviours with TestData {
  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]

  val controller =
    new VatClientInvitationsController(invitationsService)(metrics, microserviceAuthConnector, auditService) {
      override val authConnector: PlayAuthConnector = mockPlayAuthConnector
    }

  val invitationDbId: String = BSONObjectID.generate.stringify
  val invitationId: InvitationId = InvitationId("CBBBBBBBBBBCC")
  val generator = new Generator()
  val vrn: Vrn = vrn

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  "getDetailsForClient" should {
    "Return NoPermissionOnClient when given vrn does not match authVrn" in {
      clientAuthStub(clientVrnEnrolments)

      val response = await(controller.getDetailsForClient(Vrn("invalid"))(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }
  }

  "getInvitation" should {
    "Return NoPermissionOnClient when given vrn does not match authVrn" in {
      clientAuthStub(clientVrnEnrolments)

      val response = await(controller.getInvitation(Vrn("invalid"), invitationId)(FakeRequest()))

      response shouldBe NoPermissionOnClient
    }
  }
}
