/*
 * Copyright 2018 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlEqualTo}
import uk.gov.hmrc.agentclientauthorisation.support.AppAndStubs
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class RelationshipsConnectorISpec extends UnitSpec with AppAndStubs {

  val connector: RelationshipsConnector = app.injector.instanceOf[RelationshipsConnector]

  "getActiveRelationships" should {

    "return the map of active relationships when multiple present" in {
      stubFor(
        get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""{
                           |  "HMRC-MTD-IT": ["AARN1187295", "AARN1187296"],
                           |  "HMRC-MTD-VAT": ["AARN1187259"],
                           |  "HMRC-TERS-ORG": ["AARN1187259"]
                           |}""".stripMargin)))

      val result = await(connector.getActiveRelationships)
      result("HMRC-MTD-IT") should contain.only(Arn("AARN1187295"), Arn("AARN1187296"))
      result("HMRC-MTD-VAT") should contain.only(Arn("AARN1187259"))
      result("HMRC-TERS-ORG") should contain.only(Arn("AARN1187259"))
    }

    "return the map of active relationships when only one present" in {
      stubFor(
        get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""{
                           |  "HMRC-MTD-IT": ["AARN1187295"]
                           |}""".stripMargin)))

      val result = await(connector.getActiveRelationships)

      result("HMRC-MTD-IT") should contain.only(Arn("AARN1187295"))
      result.get("HMRC-MTD-VAT") shouldBe None
      result.get("HMRC-TERS-ORG") shouldBe None
    }

    "return an empty map of active relationships when none available" in {
      stubFor(
        get(urlEqualTo(s"/agent-client-relationships/client/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""{}""".stripMargin)))

      val result = await(connector.getActiveRelationships)

      result.get("HMRC-MTD-IT") shouldBe None
      result.get("HMRC-MTD-VAT") shouldBe None
      result.get("HMRC-TERS-ORG") shouldBe None
    }
  }

  "getActiveAfiRelationships" should {

    "return the list of active relationships" in {
      stubFor(
        get(urlEqualTo(s"/agent-fi-relationship/relationships/active"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withBody(s"""[
                           |  {
                           |    "service": "service123",
                           |    "clientId": "clientId123",
                           |    "relationshipStatus": "Active"
                           |  }
                           |]""".stripMargin)))

      val result = await(connector.getActiveAfiRelationships)
      result should not be empty
    }
  }
}
