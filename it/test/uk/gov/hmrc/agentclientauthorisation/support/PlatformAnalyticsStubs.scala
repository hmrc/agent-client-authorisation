/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.libs.json.{Json, OWrites}
import uk.gov.hmrc.agentclientauthorisation.model.{DimensionValue, Event}

trait PlatformAnalyticsStubs extends Eventually with IntegrationPatience {
  me: StartAndStopWireMock =>

  private implicit val dimensionWrites: OWrites[DimensionValue] = Json.writes[DimensionValue]
  private implicit val eventWrites: OWrites[Event] = Json.writes[Event]

  def verifySingleEventAnalyticsRequestSent(events: List[Event]): Unit =
    eventually {
      verify(
        1,
        postRequestedFor(urlPathEqualTo(platformAnalyticsUrl))
          .withRequestBody(similarToJson(s"""{
                                            |  "gaTrackingId": "token",
                                            |  "events": ${Json.toJson(events)}
                                            |}"""))
      )
    }

  def verifyAnalyticsRequestSent(times: Int): Unit =
    eventually {
      verify(
        times,
        postRequestedFor(urlPathEqualTo(platformAnalyticsUrl))
      )
    }

  def verifyAnalyticsRequestWasNotSent() = verifyAnalyticsRequestSent(0)

  def givenPlatformAnalyticsRequestSent(events: List[Event]): StubMapping =
    stubFor(
      post(urlPathMatching(platformAnalyticsUrl))
        .withRequestBody(similarToJson(s"""{
                                        |  "gaClientId": "clientId",
                                        |  "gaTrackingId": "token",
                                        |  "events": ${Json.toJson(events)}
                                        |}"""))
        .willReturn(aResponse().withStatus(200))
    )

  def givenPlatformAnalyticsRequestSent(singleEventInReq: Boolean): StubMapping =
    stubFor(
      post(urlPathMatching(platformAnalyticsUrl))
        .withRequestBody(matchingJsonPath(if (singleEventInReq) { "$[?(@.events.size() == 1)]" }
        else { "$[?(@.events.size() == 2)]" }))
        .willReturn(aResponse().withStatus(200))
    )

  private val platformAnalyticsUrl = "/platform-analytics/event"

  private def similarToJson(value: String) = equalToJson(value.stripMargin, false, false)

}
