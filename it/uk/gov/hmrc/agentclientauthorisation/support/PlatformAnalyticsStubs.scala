package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, equalToJson, matchingJsonPath, post, postRequestedFor, stubFor, urlPathEqualTo, urlPathMatching, verify}
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.{DimensionValue, Event}



trait PlatformAnalyticsStubs extends Eventually {
  me: StartAndStopWireMock =>

  override implicit val patienceConfig = PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

  private implicit val dimensionWrites = Json.writes[DimensionValue]
  private implicit val eventWrites = Json.writes[Event]

  def verifyAnalyticsRequestSent(count: Int, events: List[Event]): Unit =
    eventually {
      verify(
        count,
        postRequestedFor(urlPathEqualTo(platformAnalyticsUrl))
          .withRequestBody(similarToJson(s"""{
                                            |  "gaClientId": "clientId",
                                            |  "gaTrackingId": "token",
                                            |  "events": ${Json.toJson(events)}
                                            |}"""))
      )
    }

  def verifyAnalyticsRequestSent(times: Int): Unit =
    eventually{
      verify(
        times,
        postRequestedFor(urlPathEqualTo(platformAnalyticsUrl))
      )
    }

  def verifyAnalyticsRequestWasNotSent() = verifyAnalyticsRequestSent(0)

  def givenPlatformAnalyticsRequestSent(events: List[Event]): StubMapping =
    stubFor(post(urlPathMatching(platformAnalyticsUrl))
      .withRequestBody(similarToJson(s"""{
                                        |  "gaClientId": "clientId",
                                        |  "gaTrackingId": "token",
                                        |  "events": ${Json.toJson(events)}
                                        |}"""))
      .willReturn(aResponse().withStatus(200))
    )

  def givenPlatformAnalyticsRequestSent(singleEventInReq: Boolean): StubMapping =
    stubFor(post(urlPathMatching(platformAnalyticsUrl))
        .withRequestBody(matchingJsonPath(if(singleEventInReq){"$[?(@.events.size() == 1)]"} else{"$[?(@.events.size() == 2)]"}))
      .willReturn(aResponse().withStatus(200))
    )


  private val platformAnalyticsUrl = "/platform-analytics/event"

  private def similarToJson(value: String) = equalToJson(value.stripMargin, false, false)

}
