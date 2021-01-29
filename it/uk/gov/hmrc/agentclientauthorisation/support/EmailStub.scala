package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import org.scalatest.concurrent.Eventually.eventually
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation

trait EmailStub {

  me: StartAndStopWireMock =>

  def givenEmailSent(emailInformation: EmailInformation) = {
    val emailInformationJson = Json.toJson(emailInformation).toString()

    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .withRequestBody(similarToJson(emailInformationJson))
        .willReturn(aResponse().withStatus(202)))

  }

  def givenEmailReturns500 = {
    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .willReturn(aResponse().withStatus(500)))
  }

  def verifyEmailRequestWasSent(times: Int): Unit =
    eventually{
      verify(
        times,
        postRequestedFor(urlPathEqualTo("/hmrc/email"))
      )
    }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}
