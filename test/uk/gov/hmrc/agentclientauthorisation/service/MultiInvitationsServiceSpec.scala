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

package uk.gov.hmrc.agentclientauthorisation.service

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.{eq => eqs}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.repository.{MultiInvitationRecord, MultiInvitationRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.ArgumentMatchers.{eq => eqs, _}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class MultiInvitationsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val multiInvitationsRepository: MultiInvitationRepository = mock[MultiInvitationRepository]
  val auditService: AuditService = mock[AuditService]
  val metrics: Metrics = new Metrics {
    override def defaultRegistry: MetricRegistry = new MetricRegistry()
    override def toJson: String = ""
  }

  val service = new MultiInvitationsService(multiInvitationsRepository, auditService, "10 days", metrics)

  val ninoAsString: String = nino1.value

  implicit val hc = HeaderCarrier()
  implicit val request: Request[Any] = FakeRequest()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(multiInvitationsRepository)
  }

  "create" should {
    "create a multi Invitation record in the repository" in {

      when(multiInvitationsRepository.create(any[MultiInvitationRecord])(any())).thenReturn(Future successful 1)

      val response = await(service.create(Arn(arn), "uid12345", invitationIds, "personal"))

      response shouldBe 1
    }
  }
}
