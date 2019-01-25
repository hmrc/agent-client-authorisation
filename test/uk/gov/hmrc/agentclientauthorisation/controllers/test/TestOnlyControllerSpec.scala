/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers.test
import com.kenshoo.play.metrics.Metrics
import javax.inject.Provider
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.{AgentReferenceController, test}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentclientauthorisation.service.{AgentLinkService, InvitationsService}
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData, TransitionInvitation}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.{ExecutionContextExecutor, Future}

class TestOnlyControllerSpec
    extends AkkaMaterializerSpec with ResettingMockitoSugar with BeforeAndAfterEach with TransitionInvitation
    with TestData {

  val mockAgentReferenceRepository: AgentReferenceRepository = resettingMock[AgentReferenceRepository]
  val mockInvitationsRepository: InvitationsRepository = resettingMock[InvitationsRepository]
  val mockInvitationsService: InvitationsService = resettingMock[InvitationsService]
  val metrics: Metrics = resettingMock[Metrics]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val mockAgentServicesAccountConnector: AgentServicesAccountConnector = resettingMock[AgentServicesAccountConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)
  val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }

  val mockAgentLinkService: AgentLinkService =
    new AgentLinkService(mockAgentReferenceRepository, mockAgentServicesAccountConnector, auditService, metrics)

  val testOnlyController =
    new TestOnlyController(mockAgentReferenceRepository)(metrics, ecp)

  "getAgentReferenceRecord via ARN" should {

    "return an agent reference record for a given uid" in {
      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      val testOnlyAgentRefRecord: TestOnlyAgentRefRecord =
        test.TestOnlyAgentRefRecord("ABCDEFGH", arn, "stan-lee")

      when(mockAgentReferenceRepository.findByArn(any())(any()))
        .thenReturn(Future.successful(Some(agentReferenceRecord)))

      val result = await(testOnlyController.getAgentReferenceRecordByArn(arn)(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result).as[TestOnlyAgentRefRecord] shouldBe testOnlyAgentRefRecord
    }
  }
}
