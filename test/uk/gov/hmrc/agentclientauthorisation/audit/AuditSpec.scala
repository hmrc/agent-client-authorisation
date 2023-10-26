/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.audit

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.concurrent.Eventually
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.{ClientIdentifier, Service}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.mtdItId1
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, RequestId, SessionId}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

import scala.concurrent.ExecutionContext

class AuditSpec extends UnitSpec with MockitoSugar with Eventually {

  "auditEvent" should {

    "send an AgentClientRelationshipCreated Event" in {
      val mockConnector = mock[AuditConnector]
      val service = new AuditService(mockConnector)

      val hc = HeaderCarrier(
        authorization = Some(Authorization("dummy bearer token")),
        sessionId = Some(SessionId("dummy session id")),
        requestId = Some(RequestId("dummy request id")))

      val ec = concurrent.ExecutionContext.Implicits.global

      val arn: Arn = Arn("HX2345")
      val invitationId: String = "ABBBBBBBBBBCC"

      await(
        service.sendAgentClientRelationshipCreated(invitationId, arn, ClientIdentifier(mtdItId1), Service.MtdIt, isAltIsa = false)(
          hc,
          FakeRequest("GET", "/path"),
          ec))

      eventually {
        val captor = ArgumentCaptor.forClass(classOf[DataEvent])
        verify(mockConnector).sendEvent(captor.capture())(any[HeaderCarrier], any[ExecutionContext])
        val sentEvent = captor.getValue.asInstanceOf[DataEvent]

        sentEvent.auditType shouldBe "AgentClientRelationshipCreated"
        sentEvent.auditSource shouldBe "agent-client-authorisation"
        sentEvent.detail("invitationId") shouldBe "ABBBBBBBBBBCC"
        sentEvent.detail("agentReferenceNumber") shouldBe "HX2345"
        sentEvent.detail("clientIdType") shouldBe "MTDITID"
        sentEvent.detail("clientId") shouldBe "mtdItId"
        sentEvent.detail("service") shouldBe "HMRC-MTD-IT"
        sentEvent.detail("altITSASource") shouldBe "false"

        sentEvent.tags.contains("Authorization") shouldBe false

        sentEvent.tags("transactionName") shouldBe "agent-client-relationship-created"
        sentEvent.tags("path") shouldBe "/path"
        sentEvent.tags("X-Session-ID") shouldBe "dummy session id"
        sentEvent.tags("X-Request-ID") shouldBe "dummy request id"
      }
    }
  }

}
