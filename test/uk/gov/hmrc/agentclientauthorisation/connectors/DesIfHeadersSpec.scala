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

package uk.gov.hmrc.agentclientauthorisation.connectors

import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

import java.util.UUID

class DesIfHeadersSpec extends UnitSpec with MockitoSugar {

  val appConfig: AppConfig = mock[AppConfig]
  val underTest = new DesIfHeaders(appConfig)

  when(appConfig.ifEnvironment).thenReturn("ifEnvironment")
  when(appConfig.ifAuthTokenAPI1712).thenReturn("ifAuthTokenAPI1712")
  when(appConfig.ifAuthTokenAPI1495).thenReturn("ifAuthTokenAPI1495")
  when(appConfig.desEnvironment).thenReturn("desEnvironment")
  when(appConfig.desAuthToken).thenReturn("desAuthToken")

  "outboundHeaders" when {

    "is DES API" when {

      "apiName is not provided" should {
        "contain correct headers" in {
          val viaIF = false
          val apiName = None

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "desEnvironment")

          headersMap should contain("Authorization" -> "Bearer desAuthToken")
        }
      }

      "apiName is provided" should {
        "contain correct headers" in {
          val viaIF = false
          val apiName = Some("fancyApi")

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "desEnvironment")

          headersMap should contain("Authorization" -> "Bearer desAuthToken")
        }
      }

      "apiName provided is empty" should {
        "contain correct headers" in {
          val viaIF = false
          val apiName = Some("")

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "desEnvironment")

          headersMap should contain("Authorization" -> "Bearer desAuthToken")
        }
      }
    }

    "is IF API" when {

      "apiName is not provided" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = None

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "ifEnvironment")

          headersMap should not contain key("Authorization")
        }
      }

      "apiName provided is not well known" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("fancyApi")

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "ifEnvironment")

          headersMap should not contain key("Authorization")
        }
      }

      "apiName provided is 'getTrustName'" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("getTrustName")

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "ifEnvironment")

          headersMap should contain("Authorization" -> "Bearer ifAuthTokenAPI1495")
        }
      }

      "apiName provided is 'GetPptSubscriptionDisplay'" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("GetPptSubscriptionDisplay")

          val headersMap = underTest.outboundHeaders(viaIF, apiName).toMap

          assertBaseHeaders(headersMap, "ifEnvironment")

          headersMap should contain("Authorization" -> "Bearer ifAuthTokenAPI1712")
        }
      }
    }
  }

  private def assertBaseHeaders(headersMap: Map[String, String], environment: String) = {
    headersMap should contain("Environment" -> environment)
    headersMap should contain key ("CorrelationId")
    UUID.fromString(headersMap("CorrelationId")) should not be null
  }
}
