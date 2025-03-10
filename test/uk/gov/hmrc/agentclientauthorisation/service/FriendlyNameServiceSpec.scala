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
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, EnrolmentStoreProxyConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{DetailsForEmail, Invitation}
import uk.gov.hmrc.agentclientauthorisation.repository.MongoAgentReferenceRepository
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, CbcIdType, Identifier, Service}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class FriendlyNameServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  val mockAgentReferenceRepository: MongoAgentReferenceRepository = mock[MongoAgentReferenceRepository]
  val auditService: AuditService = mock[AuditService]
  val metrics: Metrics = new Metrics {
    override def defaultRegistry: MetricRegistry = new MetricRegistry()
//    override def toJson: String = ""
  }
  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockAcrConnector: RelationshipsConnector = mock[RelationshipsConnector]
  val mockAppConfig: AppConfig = mock[AppConfig]
  val service =
    new AgentLinkService(mockAgentReferenceRepository, mockDesConnector, metrics, mockAcrConnector, mockAppConfig)

  val ninoAsString: String = nino1.value

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val request: Request[Any] = FakeRequest()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAgentReferenceRepository)
  }

  "updateFriendlyName" should {

    val friendlyName = "M, L & J Smith-Doreen's Ltd."
    val friendlyNameEncoded = "M%2C+L+%26+J+Smith-Doreen%27s+Ltd."

    val invitation = Invitation.createNew(
      arn = Arn(arn),
      clientType = Some("personal"),
      service = Service.Vat,
      clientId = vrn,
      suppliedClientId = vrn,
      detailsForEmail = Some(
        DetailsForEmail(
          agencyEmail = "agency@test.com",
          agencyName = "Perfect Accounts Ltd",
          clientName = friendlyName
        )
      ),
      startDate = LocalDateTime.now().minusDays(1),
      expiryDate = LocalDate.now().plusMonths(1),
      origin = None
    )
    val enrolmentKey = s"HMRC-MTD-VAT~VRN~${vrn.value}"
    val cbcEnrolmentKey = s"HMRC-CBC-ORG~cbcId~XECBC8079578736~UTR~8130839560"

    val groupId = "0123-4567-89AB"

    "updating the client's friendly name (via ES19) when there is a non-empty client name" should {
      "succeed when there is a non empty client name" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(Some(groupId)))
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(()))

        val friendlyNameService = new FriendlyNameService(esp)

        friendlyNameService.updateFriendlyName(invitation).futureValue shouldBe (())

        verify(esp, times(1))
          .updateEnrolmentFriendlyName(eqs(groupId), eqs(enrolmentKey), eqs(friendlyNameEncoded))(any[HeaderCarrier], any[ExecutionContext])
      }
      "succeed for CBC UK with extra ES20 call" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(Some(groupId)))
        when(esp.queryKnownFacts(any[Service], any[Seq[Identifier]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(Seq[Identifier](Identifier("UTR", "8130839560")))))
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(()))

        val friendlyNameService = new FriendlyNameService(esp)

        val cbcInvitation = invitation.copy(clientId = cbcUk, suppliedClientId = cbcUk, service = Service.Cbc)

        friendlyNameService.updateFriendlyName(cbcInvitation).futureValue shouldBe (())

        verify(esp, times(1))
          .updateEnrolmentFriendlyName(eqs(groupId), eqs(cbcEnrolmentKey), eqs(friendlyNameEncoded))(any[HeaderCarrier], any[ExecutionContext])

        verify(esp, times(1))
          .queryKnownFacts(eqs(Service.Cbc), eqs(Seq(Identifier(CbcIdType.id, cbcInvitation.clientId.value))))(
            any[HeaderCarrier],
            any[ExecutionContext]
          )

      }
      "not be done (but return successfully) if there are no client details available" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(Some(groupId)))
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(()))

        val friendlyNameService = new FriendlyNameService(esp)

        val invitationWithoutDetails = invitation.copy(detailsForEmail = None)

        friendlyNameService.updateFriendlyName(invitationWithoutDetails).futureValue shouldBe (())

        verify(esp, times(0)).updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
      }
      "not be done (but return successfully) for cbcUk service if ES20 do not return UTR" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(Some(groupId)))
        when(esp.queryKnownFacts(any[Service], any[Seq[Identifier]])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(Some(Seq.empty[Identifier])))
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(()))

        val friendlyNameService = new FriendlyNameService(esp)

        val cbcInvitation = invitation.copy(clientId = cbcUk, suppliedClientId = cbcUk, service = Service.Cbc)

        friendlyNameService.updateFriendlyName(cbcInvitation).futureValue shouldBe (())

        verify(esp, times(0))
          .updateEnrolmentFriendlyName(eqs(groupId), eqs(cbcEnrolmentKey), eqs(friendlyNameEncoded))(any[HeaderCarrier], any[ExecutionContext])

        verify(esp, times(1))
          .queryKnownFacts(eqs(Service.Cbc), eqs(Seq(Identifier(CbcIdType.id, cbcInvitation.clientId.value))))(
            any[HeaderCarrier],
            any[ExecutionContext]
          )

      }
      "not be done (but return successfully) if the available client name is empty" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(Some(groupId)))
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(()))

        val friendlyNameService = new FriendlyNameService(esp)

        val invitationWithoutName = invitation.copy(detailsForEmail = invitation.detailsForEmail.map(_.copy(clientName = "")))

        friendlyNameService.updateFriendlyName(invitationWithoutName).futureValue shouldBe (())

        verify(esp, times(0)).updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext])
      }
      "not cause the invitation acceptance to fail in case of an ES19 error" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(Some(groupId)))
        // ES19 fails
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.failed(UpstreamErrorResponse("error", 503)))

        val friendlyNameService = new FriendlyNameService(esp)

        friendlyNameService.updateFriendlyName(invitation).futureValue shouldBe (()) // method response is still successful

        verify(esp, times(1))
          .updateEnrolmentFriendlyName(eqs(groupId), eqs(enrolmentKey), eqs(friendlyNameEncoded))(any[HeaderCarrier], any[ExecutionContext])
      }
      "not cause the invitation acceptance to fail if the agent's group id cannot be retrieved" in {
        val esp: EnrolmentStoreProxyConnector = mock[EnrolmentStoreProxyConnector]
        // ES1 fails
        when(esp.getPrincipalGroupIdFor(any[Arn])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.failed(UpstreamErrorResponse("error", 503)))
        when(esp.updateEnrolmentFriendlyName(any[String], any[String], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(()))
        val friendlyNameService = new FriendlyNameService(esp)

        friendlyNameService.updateFriendlyName(invitation).futureValue shouldBe (()) // method response is still successful

        verify(esp, times(0))
          .updateEnrolmentFriendlyName(eqs(groupId), eqs(enrolmentKey), eqs(friendlyName))(any[HeaderCarrier], any[ExecutionContext])
      }
    }
  }
}
