/*
 * Copyright 2022 HM Revenue & Customs
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
import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.Results._
import play.api.mvc.{AnyContent, Request, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.GenericForbidden
import uk.gov.hmrc.agentclientauthorisation.support.{TestData, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service.PersonalIncomeRecord
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItIdType, NinoType, Service}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, InsufficientEnrolments, PlayAuthConnector}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AuthConnectorSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TestData {

  val mockPlayAuthConnector: PlayAuthConnector = mock[PlayAuthConnector]
  val mockMetrics: Metrics = mock[Metrics]
  val appConfig: AppConfig = mock[AppConfig]
  val cc = stubControllerComponents()
  val mockAuthConnector: AuthActions = new AuthActions(mockMetrics, appConfig, mockPlayAuthConnector, cc)

  private type AgentAuthAction = Request[AnyContent] => Arn => Future[Result]
  private type ClientAuthAction = Request[AnyContent] => ClientId => Future[Result]

  val agentAction: AgentAuthAction = { _ => _ =>
    Future successful Ok
  }
  val clientAction: ClientAuthAction = { _ => _ =>
    Future successful Ok
  }

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[Predicate], any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(returnValue)

  private def clientAuthStub(returnValue: Future[Enrolments]) =
    when(
      mockPlayAuthConnector
        .authorise(any[Predicate], any[Retrieval[Enrolments]]())(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(returnValue)

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

    "return Forbidden when auth throws an InsufficientEnrolments error" in {
      agentAuthStub(Future failed InsufficientEnrolments())

      val response: Result = await(mockAuthConnector.onlyForAgents(agentAction).apply(FakeRequest()))

      status(response) shouldBe 403
      response shouldBe GenericForbidden
    }
  }

  "onlyForClients" should {
    "successfully grant access to a Client with HMRC-MTD-IT enrolment and Service is MTD-IT" in {
      clientAuthStub(clientMtdItEnrolments)

      val response: Result =
        await(mockAuthConnector.onlyForClients(Service.MtdIt, MtdItIdType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "successfully grant access to a Client with HMRC-NI enrolment and Service is PersonalIncomeRecord" in {
      clientAuthStub(clientNiEnrolments)

      val response = await(mockAuthConnector.onlyForClients(Service.PersonalIncomeRecord, NinoType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe OK
    }

    "return FORBIDDEN when the user has no HMRC-NI enrolment and Service is PersonalIncomeRecord" in {
      clientAuthStub(clientNoEnrolments)

      val response: Result = await(mockAuthConnector.onlyForClients(Service.PersonalIncomeRecord, MtdItIdType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when the user has no HMRC-MTD-IT enrolment and Service is MTD-IT" in {
      clientAuthStub(clientNoEnrolments)

      val response: Result =
        await(mockAuthConnector.onlyForClients(Service.MtdIt, MtdItIdType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when the user has HMRC-MTD-IT enrolment and Service is PersonalIncomeRecord" in {
      clientAuthStub(clientMtdItEnrolments)

      val response: Result =
        await(mockAuthConnector.onlyForClients(PersonalIncomeRecord, MtdItIdType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return FORBIDDEN when the user has HMRC-NI enrolment and Service is MTD-IT" in {
      clientAuthStub(clientNiEnrolments)

      val response: Result =
        await(mockAuthConnector.onlyForClients(Service.MtdIt, MtdItIdType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe FORBIDDEN
    }

    "return UNAUTHORISED when auth throws an error" in {
      clientAuthStub(failedStubForClient)

      val response: Result =
        await(mockAuthConnector.onlyForClients(Service.MtdIt, MtdItIdType)(clientAction).apply(FakeRequest()))

      status(response) shouldBe UNAUTHORIZED
    }
  }
}
