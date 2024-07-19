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

package uk.gov.hmrc.agentclientauthorisation.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{TrustName, TrustResponse}
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.{TrustTaxIdentifier, Urn, Utr}

import scala.concurrent.ExecutionContext.Implicits.global

class IfConnectorEnabledISpec extends UnitSpec with AppAndStubs {

  override protected def additionalConfiguration =
    super.additionalConfiguration ++ Map(
      "des-if.enabled"                                       -> true,
      "microservice.services.if.environment"                 -> "ifEnv",
      "microservice.services.if.authorization-token.API1712" -> "ifToken",
      "microservice.services.if.authorization-token.API1495" -> "ifToken"
    )

  val utr = Utr("0123456789")
  val urn = Urn("XXTRUST12345678")

  def connector = app.injector.instanceOf[IfConnector]

  "API 1495 Get Trust Name with IF enabled" should {
    "have correct headers and call IF selecting the correct url when passed a UTR " in {
      getTrustNameIF(utr, 200, trustNameJson)
      val result = await(connector.getTrustName(utr.value))

      result shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-IF-getTrustName-GET")
    }

    "have correct headers and call IF selecting the correct url when passed a URN " in {
      getTrustNameIF(urn, 200, trustNameJson)
      val result = await(connector.getTrustName(urn.value))

      result shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-IF-getTrustName-GET")
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
            .withBody(response)
        )
    )
  }

}
