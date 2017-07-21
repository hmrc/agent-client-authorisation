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
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.support.ResettingMockitoSugar
import uk.gov.hmrc.play.test.UnitSpec

class AuthConnectorSpec extends UnitSpec with ResettingMockitoSugar {

  private val metrics = resettingMock[Metrics]

  "authorityFromJson" should {
    "resolve the enrolments URL relative to the authority URL" in {
      val authConnector = new AuthConnector(new URL("http://localhost"), null, metrics)

      val authority = authConnector.authorityFromJson(
        authorityUrl = new URL("http://localhost/auth/authority"),
        authorityJson = Json.obj(
          "enrolments" -> "relativeEnrolments"
        ))

      // URLs in the authority should be relative to the URL the authority is fetched from, which is http://localhost/auth/authority
      // They should NOT be resolved relative to the auth service's base URL because that is not where the authority is fetched from.
      // This is similar to the way a web browser resolves relative URLs - they are resolved relative to the URL of the page they appear in.
      authority.enrolmentsUrl shouldBe new URL("http://localhost/auth/relativeEnrolments")
    }
  }

}
