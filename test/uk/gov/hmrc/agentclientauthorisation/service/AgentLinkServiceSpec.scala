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

package uk.gov.hmrc.agentclientauthorisation.service

import com.codahale.metrics.MetricRegistry
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.DesConnector
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model.{AgentDetailsDesResponse, BusinessAddress}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{TransitionInvitation, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, SuspensionDetails, Utr}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AgentLinkServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val mockAgentReferenceRepository: MongoAgentReferenceRepository = mock[MongoAgentReferenceRepository]
  val auditService: AuditService = mock[AuditService]
  val metrics: Metrics = new Metrics {
    override def defaultRegistry: MetricRegistry = new MetricRegistry()
//    override def toJson: String = ""
  }
  val mockDesConnector: DesConnector = mock[DesConnector]

  val service =
    new AgentLinkService(mockAgentReferenceRepository, mockDesConnector, metrics)

  val ninoAsString: String = nino1.value

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val request: Request[Any] = FakeRequest()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAgentReferenceRepository)
  }

  "getAgentLink" should {

    val businessAddress = BusinessAddress("25 Any Street", None, None, None, Some("AA1 7YY"), "GB")

    "return a link when agent reference record already exists" in {
      val agentReferenceRecord = AgentReferenceRecord("ABCDEFGH", Arn(arn), Seq("obi-wan"))

      when(mockAgentReferenceRepository.findByArn(any[Arn]))
        .thenReturn(Future successful Some(agentReferenceRecord))
      when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Some(
              AgentDetailsDesResponse(
                Some(utr),
                Option(model.AgencyDetails(Some("stan-lee"), Some("email"), Some("phone"), Some(businessAddress))),
                Some(SuspensionDetails(suspensionStatus = false, None))
              )
            )
          )
        )
      when(mockAgentReferenceRepository.updateAgentName(eqs("ABCDEFGH"), eqs("stan-lee")))
        .thenReturn(Future.successful(()))

      val response = await(service.getInvitationUrl(Arn(arn), "personal"))

      response shouldBe "/invitations/personal-taxes/manage-who-can-deal-with-HMRC-for-you/ABCDEFGH/stan-lee"
    }

    "create new agent reference record and return a link" in {
      when(mockAgentReferenceRepository.findByArn(any[Arn])).thenReturn(Future successful None)
      when(mockAgentReferenceRepository.create(any[AgentReferenceRecord]))
        .thenReturn(Future successful Some("id"))
      when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Some(
              AgentDetailsDesResponse(
                Some(utr),
                Option(model.AgencyDetails(Some("stan-lee"), Some("email"), Some("phone"), Some(businessAddress))),
                Some(SuspensionDetails(suspensionStatus = false, None))
              )
            )
          )
        )

      val response = await(service.getInvitationUrl(Arn(arn), "personal"))

      response should fullyMatch regex "/invitations/personal-taxes/manage-who-can-deal-with-HMRC-for-you/[A-Z0-9]{8}/stan-lee"
    }

    "return uid and normalisedAgentName" in {
      val agentReferenceRecord = AgentReferenceRecord("ABCDEFGH", Arn(arn), Seq("obi-wan"))

      when(mockAgentReferenceRepository.findByArn(any[Arn]))
        .thenReturn(Future successful Some(agentReferenceRecord))
      when(mockDesConnector.getAgencyDetails(any[Either[Utr, Arn]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(
          Future.successful(
            Some(
              AgentDetailsDesResponse(
                Some(utr),
                Option(model.AgencyDetails(Some("stan-lee"), Some("email"), Some("phone"), Some(businessAddress))),
                Some(SuspensionDetails(suspensionStatus = false, None))
              )
            )
          )
        )
      when(mockAgentReferenceRepository.updateAgentName(eqs("ABCDEFGH"), eqs("stan-lee")))
        .thenReturn(Future.successful(()))

      val response = await(service.getAgentInvitationUrlDetails(Arn(arn)))

      response shouldBe ("ABCDEFGH", "stan-lee")
    }

    "make invitation link" in {
      val response = service.makeInvitationUrl("clientType", "uid", "normalisedAgentName")
      response shouldBe "/invitations/clientType-taxes/manage-who-can-deal-with-HMRC-for-you/uid/normalisedAgentName"
    }
  }
}
