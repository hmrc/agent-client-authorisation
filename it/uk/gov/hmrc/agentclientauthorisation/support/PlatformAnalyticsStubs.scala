package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.{DimensionValue, Event}



trait PlatformAnalyticsStubs extends Eventually with IntegrationPatience {
  me: StartAndStopWireMock =>

  private implicit val dimensionWrites = Json.writes[DimensionValue]
  private implicit val eventWrites = Json.writes[Event]

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
