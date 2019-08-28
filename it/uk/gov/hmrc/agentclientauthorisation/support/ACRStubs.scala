package uk.gov.hmrc.agentclientauthorisation.support

import uk.gov.hmrc.domain.TaxIdentifier

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

trait ACRStubs {

  wm: StartAndStopWireMock =>

  def givenCreateRelationship(arn: Arn, service: String, identifierKey: String, taxIdentifier: TaxIdentifier) = {
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$service/client/$identifierKey/${taxIdentifier.value}"))
        .willReturn(
          aResponse()
            .withStatus(201)
        )
    )
  }

}
