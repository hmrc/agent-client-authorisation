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
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Mockito._
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.service.MultiInvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId

import scala.concurrent.Future

class MultiInvitationControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with TestData {

  val mockMultiInvitationsRepository: AgentReferenceRepository = resettingMock[AgentReferenceRepository]
  val metrics: Metrics = resettingMock[Metrics]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)

  val mockMultiInvitationsService: MultiInvitationsService =
    new MultiInvitationsService(mockMultiInvitationsRepository, auditService, metrics)

  val controller =
    new MultiInvitationController(mockMultiInvitationsService)(metrics, mockPlayAuthConnector, auditService)

  "getMultiInvitationRecord" should {

    "return a multi invitation record from a given uid" in {
      val multiInvitationRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      when(mockMultiInvitationsRepository.findBy(any())(any()))
        .thenReturn(Future.successful(Some(multiInvitationRecord)))

      val result = await(controller.getMultiInvitationRecord("ABCDEFGH")(FakeRequest()))
      status(result) shouldBe 200
      jsonBodyOf(result).as[AgentReferenceRecord] shouldBe multiInvitationRecord
    }

    "return none when the record is not found" in {
      when(mockMultiInvitationsRepository.findBy(any())(any()))
        .thenReturn(Future.successful(None))

      val result = await(controller.getMultiInvitationRecord("ABCDEFGH")(FakeRequest()))
      status(result) shouldBe 404
    }

    "return failure when unable to fetch record from mongo" in {
      when(mockMultiInvitationsRepository.findBy(any())(any()))
        .thenReturn(Future.failed(new Exception("Error")))

      an[Exception] shouldBe thrownBy {
        await(controller.getMultiInvitationRecord("ABCDEFGH")(FakeRequest()))
      }
    }
  }
}
