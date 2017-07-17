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

import java.net.URL

import com.kenshoo.play.metrics.Metrics
import org.mockito.Matchers.{any, eq => eqs}
import org.mockito.Mockito.{verify, when}
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.agentclientauthorisation.model.{AuthEnrolment, EnrolmentIdentifier}
import uk.gov.hmrc.agentclientauthorisation.support.ResettingMockitoSugar
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthConnectorSpec extends UnitSpec with ResettingMockitoSugar {

  private implicit val hc = resettingMock[HeaderCarrier]

  private val httpGet = resettingMock[HttpGet]
  private val metrics = resettingMock[Metrics]
  private val authConnector = new AuthConnector(new URL("http://localhost"), httpGet, metrics)

  private val authorityUrl = "http://localhost/auth/authority"
  private val authorityJsonWithRelativeUrl = Json.obj(
    "enrolments" -> "relativeEnrolments"
  )

  /**
    * URLs in the authority should be relative to the URL the authority is fetched from, which is http://localhost/auth/authority
    * They should NOT be resolved relative to the auth service's base URL because that is not where the authority is fetched from.
    * This is similar to the way a web browser resolves relative URLs - they are resolved relative to the URL of the page they appear in.
    **/
  private val expectedEnrolmentsUrl = "http://localhost/auth/relativeEnrolments"


  "enrolmentsAbsoluteUrl" should {
    "resolve the enrolments URL relative to the authority URL" in {
      authConnector.enrolmentsAbsoluteUrl(
        authorityUrl = new URL(authorityUrl),
        authorityJson = Json.obj(
          "enrolments" -> "relativeEnrolments"
        )) shouldBe new URL(expectedEnrolmentsUrl)
    }
  }

  "findArn" should {
    "resolve the enrolments URL relative to the authority URL" in {

      when(httpGet.GET[JsValue](eqs(authorityUrl))(any[HttpReads[JsValue]], any[HeaderCarrier]))
        .thenReturn(Future successful authorityJsonWithRelativeUrl)

      val authority: Authority = await(authConnector.currentAuthority)

      val asAgentEnrolment = AuthEnrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", "TARN0000001")), "Activated")

      when(httpGet.GET[Set[AuthEnrolment]](eqs(expectedEnrolmentsUrl))(any[HttpReads[Set[AuthEnrolment]]], any[HeaderCarrier]))
        .thenReturn(Future successful Set(asAgentEnrolment))

      val arn: Option[Arn] = await(authority.findArn())

      convertOptionToValuable(arn).value shouldBe Arn("TARN0000001")

      verify(httpGet).GET[Set[AuthEnrolment]](eqs(expectedEnrolmentsUrl))(any[HttpReads[Set[AuthEnrolment]]], any[HeaderCarrier])
    }
  }

}
