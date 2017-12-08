/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.connectors

import com.kenshoo.play.metrics.Metrics
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentclientauthorisation.support.TestData
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.{ExecutionContext, Future}

class AuthConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TestData {

  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockMetrics: Metrics = mock[Metrics]
  val mockAuthConnector: AuthConnector = new AuthConnector(mockMetrics, mockPlayAuthConnector)

  private type AgentAuthAction = Request[AnyContent] => Arn => Future[Result]
  private type ClientAuthAction = Request[AnyContent] => MtdItId => Future[Result]

  val agentAction: AgentAuthAction = { implicit request => implicit arn => Future successful Ok }
  val clientAction: ClientAuthAction = { implicit request => implicit mtdItId => Future successful Ok }

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any(), any())).thenReturn(returnValue)

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[Enrolments]]())(any(), any())).thenReturn(returnValue)

  override def beforeEach(): Unit = reset(mockPlayAuthConnector)

  "onlyForAgents" should {
    "return OK for an Agent with HMRC-AS-AGENT enrolment" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val response: Result = await(mockAuthConnector.onlyForAgents(agentAction).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "return FORBIDDEN when the user has no HMRC-AS-AGENT enrolment" in {
      agentAuthStub(agentNoEnrolments)

      val response: Result = await(mockAuthConnector.onlyForAgents(agentAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return UNAUTHORISED when the user does not belong to Agent affinity group" in {
      agentAuthStub(agentIncorrectAffinity)

      val response: Result = await(mockAuthConnector.onlyForAgents(agentAction).apply(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when auth fails to return an AffinityGroup or Enrolments" in {
      agentAuthStub(neitherHaveAffinityOrEnrolment)

      val response: Result = await(mockAuthConnector.onlyForAgents(agentAction).apply(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
    }

    "return UNAUTHORISED when auth throws an error" in {
      agentAuthStub(failedStubForAgent)

      val response: Result = await(mockAuthConnector.onlyForAgents(agentAction).apply(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
    }
  }

  "onlyForClients" should {
    "successfully grant access to a Client with HMRC-MTD_IT enrolment" in {
      clientAuthStub(clientEnrolments)

      val response: Result = await(mockAuthConnector.onlyForClients(Service.MtdIt, CLIENT_ID_TYPE_MTDITID)(clientAction)(MtdItId.apply).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "return FORBIDDEN when the user has no HMRC-NI enrolment" in {
      clientAuthStub(clientNoEnrolments)

      val response: Result = await(mockAuthConnector.onlyForClients(Service.MtdIt, CLIENT_ID_TYPE_MTDITID)(clientAction)(MtdItId.apply).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return UNAUTHORISED when auth throws an error" in {
      clientAuthStub(failedStubForClient)

      val response: Result = await(mockAuthConnector.onlyForClients(Service.MtdIt, CLIENT_ID_TYPE_MTDITID)(clientAction)(MtdItId.apply).apply(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
    }
  }
}
