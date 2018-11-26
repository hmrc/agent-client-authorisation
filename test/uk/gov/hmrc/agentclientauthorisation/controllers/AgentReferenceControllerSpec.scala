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
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentclientauthorisation.model.Pending
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentclientauthorisation.service.AgentLinkService
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.Future

class AgentReferenceControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with TestData {

  val mockAgentReferenceRepository: AgentReferenceRepository = resettingMock[AgentReferenceRepository]
  val mockInvitationsRepository: InvitationsRepository = resettingMock[InvitationsRepository]
  val metrics: Metrics = resettingMock[Metrics]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val mockAgentServicesAccountConnector: AgentServicesAccountConnector = resettingMock[AgentServicesAccountConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)

  val mockAgentLinkService: AgentLinkService =
    new AgentLinkService(mockAgentReferenceRepository, mockAgentServicesAccountConnector, auditService, metrics)

  val agentReferenceController =
    new AgentReferenceController(mockAgentReferenceRepository, mockInvitationsRepository)(
      metrics,
      mockPlayAuthConnector,
      auditService)

  "getAgentReferenceRecord" should {

    "return an agent reference record for a given uid" in {
      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      when(mockAgentReferenceRepository.findBy(any())(any()))
        .thenReturn(Future.successful(Some(agentReferenceRecord)))

      val result = await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))

      status(result) shouldBe 200
      jsonBodyOf(result).as[AgentReferenceRecord] shouldBe agentReferenceRecord
    }

    "return none when the record is not found" in {
      when(mockAgentReferenceRepository.findBy(any())(any()))
        .thenReturn(Future.successful(None))

      val result = await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))

      status(result) shouldBe 404
    }

    "return failure when unable to fetch record from mongo" in {
      when(mockAgentReferenceRepository.findBy(any())(any()))
        .thenReturn(Future.failed(new Exception("Error")))

      an[Exception] shouldBe thrownBy {
        await(agentReferenceController.getAgentReferenceRecord("ABCDEFGH")(FakeRequest()))
      }
    }
  }
}
