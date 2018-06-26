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

package uk.gov.hmrc.agentclientauthorisation.support

import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.domain.{Nino, SaAgentReference, TaxIdentifier}

trait WiremockAware {
  def wiremockBaseUrl: String
}

trait BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def isNotLoggedIn: A = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(aResponse().withStatus(401)))
    this
  }
}

trait ClientUserAuthStubs[A] extends BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def canonicalClientId: TaxIdentifier

  def isLoggedInWithMtdEnrolment: A = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(aResponse().withStatus(200).withBody(s"""
                                                            |{
                                                            |  "allEnrolments": [
                                                            |    {
                                                            |      "key": "HMRC-MTD-IT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "MTDITID",
                                                            |          "value": "${canonicalClientId.value}"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    }
                                                            |  ]
                                                            |}
       """.stripMargin)))

    this
  }

  def isLoggedInWithNiEnrolment(nino: Nino): A = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(aResponse().withStatus(200).withBody(s"""
                                                            |{
                                                            |  "allEnrolments": [
                                                            |    {
                                                            |      "key": "HMRC-NI",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "NINO",
                                                            |          "value": "${nino.value}"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    }
                                                            |  ]
                                                            |}
       """.stripMargin)))

    this
  }

  def isLoggedInWithVATEnrolment(vrn: Vrn): A = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(aResponse().withStatus(200).withBody(s"""
                                                            |{
                                                            |  "allEnrolments": [
                                                            |    {
                                                            |      "key": "HMRC-MTD-VAT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "VRN",
                                                            |          "value": "${vrn.value}"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    }
                                                            |  ]
                                                            |}
       """.stripMargin)))

    this
  }
}

trait AgentAuthStubs[A] extends BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def arn: String

  protected var saAgentReference: Option[SaAgentReference] = None

  def isLoggedInAndIsSubscribed: A = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(aResponse().withStatus(200).withBody(s"""
                                                            |{
                                                            |  "affinityGroup": "Agent",
                                                            |  "allEnrolments": [
                                                            |    {
                                                            |      "key": "HMRC-AS-AGENT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "AgentReferenceNumber",
                                                            |          "value": "$arn"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    },
                                                            |    {
                                                            |      "key": "IR-PAYE-AGENT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "IrAgentReference",
                                                            |          "value": "HZ1234"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    },
                                                            |    {
                                                            |      "key": "HMRC-AS-AGENT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "AnotherIdentifier",
                                                            |          "value": "not the ARN"
                                                            |        },
                                                            |        {
                                                            |          "key": "AgentReferenceNumber",
                                                            |          "value": "$arn"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    }
                                                            |  ]
                                                            |}
       """.stripMargin)))
    this
  }

  def isLoggedInAndNotSubscribed: A = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(aResponse().withStatus(200).withBody(s"""
                                                            |{
                                                            |  "affinityGroup": "Agent",
                                                            |  "allEnrolments": [
                                                            |    {
                                                            |      "key": "HMRC-AGENT-AGENT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "AgentRefNumber",
                                                            |          "value": "JARN1234567"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    },
                                                            |    {
                                                            |      "key": "IR-PAYE-AGENT",
                                                            |      "identifiers": [
                                                            |        {
                                                            |          "key": "IrAgentReference",
                                                            |          "value": "HZ1234"
                                                            |        }
                                                            |      ],
                                                            |      "state": "Activated"
                                                            |    }
                                                            |  ]
                                                            |}
       """.stripMargin)))
    this
  }

}
