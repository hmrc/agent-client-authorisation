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
import org.scalatest.concurrent.Eventually.eventually
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

  def verifyCreateRelationshipNotSent(arn: Arn, service: String, identifierKey: String, taxIdentifier: TaxIdentifier): Unit =
    eventually {
      verify(
        0,
        putRequestedFor(
          urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$service/client/$identifierKey/${taxIdentifier.value}")
        )
      )
    }

  def verifyCreateRelationshipWasSent(arn: Arn, service: String, identifierKey: String, taxIdentifier: TaxIdentifier): Unit =
    eventually {
      verify(
        1,
        putRequestedFor(
          urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$service/client/$identifierKey/${taxIdentifier.value}")
        )
      )
    }

  def givenMigrateAgentReferenceRecord: StubMapping =
    stubFor(
      post(urlEqualTo("/agent-client-relationships/migrate/agent-reference-record"))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )

}
