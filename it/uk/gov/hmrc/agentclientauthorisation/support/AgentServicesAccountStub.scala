package uk.gov.hmrc.agentclientauthorisation.support

import java.nio.charset.StandardCharsets.UTF_8

import com.github.tomakehurst.wiremock.client.WireMock._
import play.utils.UriEncoding
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Vrn}
import uk.gov.hmrc.domain.Nino

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

  def givenGetAgencyEmailAgentStub(arn: Arn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/agency-email/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "agencyEmail" : "agent@email.com"
                         |}""".stripMargin)))

  def givenNotFoundAgencyEmailAgentStub(arn: Arn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/agency-email/${arn.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)))

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

  def givenTradingName(nino: Nino, tradingName: String) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/trading-name/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"tradingName": "$tradingName"}""")
        ))

  def givenTradingNameMissing(nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/trading-name/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{}""")
        ))

  def givenTradingNameNotFound(nino: Nino) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/trading-name/nino/${nino.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        ))

  def givenClientDetails(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/vat-customer-details/vrn/${vrn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"organisationName": "Gadgetron",
                         |"individual" : {
                         |    "title": "Mr",
                         |    "firstName": "Winston",
                         |    "middleName": "H",
                         |    "lastName": "Greenburg"
                         |    },
                         |"tradingName": "GDT"
                         |}""".stripMargin)
        ))
  def givenClientDetailsOnlyOrganisation(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/vat-customer-details/vrn/${vrn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"organisationName": "Gadgetron"}""".stripMargin)
        ))

  def givenClientDetailsOnlyPersonal(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/vat-customer-details/vrn/${vrn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"individual" : {
                         |    "title": "Mr",
                         |    "firstName": "Winston",
                         |    "middleName": "H",
                         |    "lastName": "Greenburg"
                         |    }
                         |}""".stripMargin)
        ))

  def givenClientDetailsNotFound(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/vat-customer-details/vrn/${vrn.value}"))
        .willReturn(
          aResponse()
            .withStatus(404)
        ))

  private def encodePathSegment(pathSegment: String): String =
    UriEncoding.encodePathSegment(pathSegment, UTF_8.name)
}
