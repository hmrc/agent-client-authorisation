package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier

trait ACRStubs {

  wm: StartAndStopWireMock =>

  def givenCreateRelationship(arn: Arn, service: String, identifierKey: String, taxIdentifier: TaxIdentifier) =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$service/client/$identifierKey/${taxIdentifier.value}"))
        .willReturn(
          aResponse()
            .withStatus(201)
        )
    )

  def givenCreateRelationshipFails(arn: Arn, service: String, identifierKey: String, taxIdentifier: TaxIdentifier) =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$service/client/$identifierKey/${taxIdentifier.value}"))
        .willReturn(
          aResponse()
            .withStatus(502)
        )
    )

  def givenClientRelationships(arn: Arn, service: String) =
    stubFor(
    get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{
                       |  "$service": ["${arn.value}"]
                       |}""".stripMargin)
      )
    )

}
