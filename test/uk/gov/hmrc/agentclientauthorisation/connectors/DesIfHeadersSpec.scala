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
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, HeaderNames, RequestId, SessionId}

import java.util.UUID

class DesIfHeadersSpec extends UnitSpec with MockitoSugar {

  val appConfig: AppConfig = mock[AppConfig]
  val underTest = new DesIfHeaders(appConfig)

  when(appConfig.ifEnvironment).thenReturn("ifEnvironment")
  when(appConfig.ifAuthTokenAPI1712).thenReturn("ifAuthTokenAPI1712")
  when(appConfig.ifAuthTokenAPI1495).thenReturn("ifAuthTokenAPI1495")
  when(appConfig.ifAuthTokenAPI2143).thenReturn("ifAuthTokenAPI2143")
  when(appConfig.ifAuthTokenAPI1171).thenReturn("ifAuthTokenAPI1171")

  when(appConfig.desEnvironment).thenReturn("desEnvironment")
  when(appConfig.desAuthToken).thenReturn("desAuthToken")
  when(appConfig.internalHostPatterns).thenReturn(Seq("\"^.*\\\\.service$\"", "^.*\\.mdtp$", "^localhost$").map(_.r))

  "headersConfig" when {

    "is DES API" when {

      "is internally hosted" should {
        "contain correct headers" in {
          val viaIF = false
          val apiName = None
          val isInternalHost = true
          val hc = HeaderCarrier(
            authorization = Some(Authorization("Bearer sessionToken")),
            sessionId = Some(SessionId("session-xyz")),
            requestId = Some(RequestId("requestId"))
          )

          val headersConfig = underTest.headersConfig(viaIF, apiName, hc, isInternalHost)

          val headers = headersConfig.explicitHeaders.toMap
          val headerCarrier = headersConfig.hc

          headers should contain("Environment" -> "desEnvironment")
          headers should contain key ("CorrelationId")
          UUID.fromString(headers("CorrelationId")) should not be null
          headers should not contain key(HeaderNames.authorisation)
          headers should not contain key(HeaderNames.xSessionId)
          headers should not contain key(HeaderNames.xRequestId)

          headerCarrier.authorization.get should be(Authorization("Bearer desAuthToken"))
          headerCarrier.sessionId.get should be(SessionId("session-xyz"))
          headerCarrier.requestId.get should be(RequestId("requestId"))
        }
      }

      "is externally hosted" should {
        "contain correct headers" in {
          val viaIF = false
          val apiName = Some("DES_API")
          val isInternalHost = false
          val hc = HeaderCarrier(
            authorization = Some(Authorization("Bearer sessionToken")),
            sessionId = Some(SessionId("session-xyz")),
            requestId = Some(RequestId("requestId"))
          )

          val headersConfig = underTest.headersConfig(viaIF, apiName, hc, isInternalHost)

          val headers = headersConfig.explicitHeaders.toMap
          val headerCarrier = headersConfig.hc

          headers should contain("Environment" -> "desEnvironment")
          headers should contain key ("CorrelationId")
          UUID.fromString(headers("CorrelationId")) should not be null
          headers should contain(HeaderNames.authorisation -> "Bearer desAuthToken")
          headers should contain(HeaderNames.xSessionId    -> "session-xyz")
          headers should contain(HeaderNames.xRequestId    -> "requestId")
          headerCarrier == hc should be(true)
        }
      }
    }

    "is IF API" when {

      "apiName is not provided" should {
        "throw an exception" in {
          val viaIF = true
          val apiName = None
          val isInternalHost = false
          val hc = HeaderCarrier()

          an[RuntimeException] shouldBe thrownBy {
            underTest.headersConfig(viaIF, apiName, hc, isInternalHost)
          }
        }
      }

      "apiName is not recognised" should {
        "throw an exception" in {
          val viaIF = true
          val apiName = Some("Unknown_API")
          val isInternalHost = false
          val hc = HeaderCarrier()

          an[RuntimeException] shouldBe thrownBy {
            underTest.headersConfig(viaIF, apiName, hc, isInternalHost)
          }
        }
      }
      "apiName provided is 'getTrustName'" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("getTrustName")
          val isInternalHost = false
          val hc = HeaderCarrier(
            authorization = Some(Authorization("Bearer sessionToken")),
            sessionId = Some(SessionId("session-xyz")),
            requestId = Some(RequestId("requestId"))
          )

          val headersConfig = underTest.headersConfig(viaIF, apiName, hc, isInternalHost)

          val headers = headersConfig.explicitHeaders.toMap
          val headerCarrier = headersConfig.hc

          headers should contain("Environment" -> "ifEnvironment")
          headers should contain key ("CorrelationId")
          UUID.fromString(headers("CorrelationId")) should not be null
          headers should contain(HeaderNames.authorisation -> "Bearer ifAuthTokenAPI1495")
          headers should contain(HeaderNames.xSessionId    -> "session-xyz")
          headers should contain(HeaderNames.xRequestId    -> "requestId")
          headerCarrier == hc should be(true)
        }
      }

      "apiName provided is 'GetPptSubscriptionDisplay'" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("GetPptSubscriptionDisplay")
          val isInternalHost = false
          val hc = HeaderCarrier(
            authorization = Some(Authorization("Bearer sessionToken")),
            sessionId = Some(SessionId("session-xyz")),
            requestId = Some(RequestId("requestId"))
          )

          val headersConfig = underTest.headersConfig(viaIF, apiName, hc, isInternalHost)

          val headers = headersConfig.explicitHeaders.toMap
          val headerCarrier = headersConfig.hc

          headers should contain("Environment" -> "ifEnvironment")
          headers should contain key ("CorrelationId")
          UUID.fromString(headers("CorrelationId")) should not be null
          headers should contain(HeaderNames.authorisation -> "Bearer ifAuthTokenAPI1712")
          headers should contain(HeaderNames.xSessionId    -> "session-xyz")
          headers should contain(HeaderNames.xRequestId    -> "requestId")
          headerCarrier == hc should be(true)
        }
      }

      "apiName provided is 'getPillar2Subscription'" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("getPillar2Subscription")
          val isInternalHost = false
          val hc = HeaderCarrier(
            authorization = Some(Authorization("Bearer sessionToken")),
            sessionId = Some(SessionId("session-xyz")),
            requestId = Some(RequestId("requestId"))
          )

          val headersConfig = underTest.headersConfig(viaIF, apiName, hc, isInternalHost)

          val headers = headersConfig.explicitHeaders.toMap
          val headerCarrier = headersConfig.hc

          headers should contain("Environment" -> "ifEnvironment")
          headers should contain key ("CorrelationId")
          UUID.fromString(headers("CorrelationId")) should not be null
          headers should contain(HeaderNames.authorisation -> "Bearer ifAuthTokenAPI2143")
          headers should contain(HeaderNames.xSessionId    -> "session-xyz")
          headers should contain(HeaderNames.xRequestId    -> "requestId")
          headerCarrier == hc should be(true)
        }
      }

      "apiName provided is 'GetRegistrationBusinessDetailsByMtdId'" should {
        "contain correct headers" in {
          val viaIF = true
          val apiName = Some("GetRegistrationBusinessDetailsByMtdId")
          val isInternalHost = false
          val hc = HeaderCarrier(
            authorization = Some(Authorization("Bearer sessionToken")),
            sessionId = Some(SessionId("session-xyz")),
            requestId = Some(RequestId("requestId"))
          )

          val headersConfig = underTest.headersConfig(viaIF, apiName, hc, isInternalHost)

          val headers = headersConfig.explicitHeaders.toMap
          val headerCarrier = headersConfig.hc

          headers should contain("Environment" -> "ifEnvironment")
          headers should contain key ("CorrelationId")
          UUID.fromString(headers("CorrelationId")) should not be null
          headers should contain(HeaderNames.authorisation -> "Bearer ifAuthTokenAPI1171")
          headers should contain(HeaderNames.xSessionId    -> "session-xyz")
          headers should contain(HeaderNames.xRequestId    -> "requestId")
          headerCarrier == hc should be(true)
        }
      }
    }
  }

}
