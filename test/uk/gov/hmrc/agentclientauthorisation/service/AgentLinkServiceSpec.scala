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

package uk.gov.hmrc.agentclientauthorisation.service

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentLinkServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val mockAgentReferenceRepository: MongoAgentReferenceRepository = mock[MongoAgentReferenceRepository]
  val mockAgentServicesAccountConnector: AgentServicesAccountConnector = mock[AgentServicesAccountConnector]
  val auditService: AuditService = mock[AuditService]
  val metrics: Metrics = new Metrics {
    override def defaultRegistry: MetricRegistry = new MetricRegistry()
    override def toJson: String = ""
  }

  val service =
    new AgentLinkService(mockAgentReferenceRepository, mockAgentServicesAccountConnector, auditService, metrics)

  val ninoAsString: String = nino1.value

  implicit val hc = HeaderCarrier()
  implicit val request: Request[Any] = FakeRequest()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAgentReferenceRepository)
  }

  "getAgentLink" should {

    "return a link when agent reference record already exists" in {
      val agentReferenceRecord = AgentReferenceRecord("ABCDEFGH", Arn(arn), Seq("obi-wan"))

      when(mockAgentReferenceRepository.findByArn(any[Arn])(any()))
        .thenReturn(Future successful Some(agentReferenceRecord))
      when(mockAgentServicesAccountConnector.getAgencyNameAgent(any(), any()))
        .thenReturn(Future.successful(Some("stan-lee")))
      when(mockAgentReferenceRepository.updateAgentName(eqs("ABCDEFGH"), eqs("stan-lee"))(any()))
        .thenReturn(Future.successful(()))

      val response = await(service.getInvitationUrl(Arn(arn), "personal"))

      response shouldBe "/invitations/personal/ABCDEFGH/stan-lee"
    }

    "create new agent reference record and return a link" in {
      when(mockAgentReferenceRepository.findByArn(any[Arn])(any())).thenReturn(Future successful None)
      when(mockAgentReferenceRepository.create(any[AgentReferenceRecord])(any())).thenReturn(Future successful 1)
      when(mockAgentServicesAccountConnector.getAgencyNameAgent(any(), any()))
        .thenReturn(Future.successful(Some("stan-lee")))

      val response = await(service.getInvitationUrl(Arn(arn), "personal"))

      response should fullyMatch regex "/invitations/personal/[A-Z0-9]{8}/stan-lee"
    }
  }
}
