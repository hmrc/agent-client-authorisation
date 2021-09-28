package uk.gov.hmrc.agentclientauthorisation.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentclientauthorisation.model.{TrustName, TrustResponse}
import uk.gov.hmrc.agentclientauthorisation.support.AppAndStubs
import uk.gov.hmrc.agentmtdidentifiers.model.{TrustTaxIdentifier, Urn, Utr}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorIFEnabledISpec extends UnitSpec with AppAndStubs {

  override protected def additionalConfiguration =
    super.additionalConfiguration ++  Map(
      "des-if.enabled"                                -> true,
      "microservice.services.if.environment"          -> "ifEnv",
      "microservice.services.if.authorization-token"  -> "ifToken"
    )

  val utr = Utr("0123456789")
  val urn = Urn("XXTRUST12345678")

  def connector = app.injector.instanceOf[DesConnector]

"API 1495 Get Trust Name with IF enabled" should {
  "have correct headers and call IF selecting the correct url when passed a UTR " in {
    getTrustNameIF(utr, 200, trustNameJson)
    val result = await(connector.getTrustName(utr.value))

    result shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))
    verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getTrustName-GET")
  }

  "have correct headers and call IF selecting the correct url when passed a URN " in {
    getTrustNameIF(urn, 200, trustNameJson)
    val result = await(connector.getTrustName(urn.value))

    result shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))
    verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getTrustName-GET")
  }
}
  private val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

  private def getTrustNameIF(trustTaxIdentifier: TrustTaxIdentifier, status: Int, response: String) = {
    val identifierType = trustTaxIdentifier match {
      case Utr(_) => "UTR"
      case Urn(_) => "URN"
    }
    stubFor(
      get(urlEqualTo(s"/trusts/agent-known-fact-check/$identifierType/${trustTaxIdentifier.value}"))
        .withHeader("authorization", equalTo("Bearer ifToken"))
        .withHeader("environment", equalTo("ifEnv"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(status)
            .withBody(response)))
  }

}
