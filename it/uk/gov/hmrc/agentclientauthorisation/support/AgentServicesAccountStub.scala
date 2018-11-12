package uk.gov.hmrc.agentclientauthorisation.support

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import play.utils.UriEncoding
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

trait AgentServicesAccountStub {
  me: StartAndStopWireMock =>

  def givenGetAgencyNameAgentStub =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/agent/agency-name"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "agencyName" : "My Agency"
                         |}""".stripMargin)))

  def givenAgencyNameNotFoundClientStub(arn: Arn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/agency-name/${encodePathSegment(arn.value)}"))
        .willReturn(aResponse()
          .withStatus(404)))

  def givenAgencyNameNotFoundAgentStub =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/agent/agency-name"))
        .willReturn(aResponse()
          .withStatus(404)))

  private def encodePathSegment(pathSegment: String): String =
    UriEncoding.encodePathSegment(pathSegment, UTF_8.name)
}
