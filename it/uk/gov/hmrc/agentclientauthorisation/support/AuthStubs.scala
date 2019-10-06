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
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, SaAgentReference}

trait WiremockAware {
  def wiremockBaseUrl: String
}

trait BasicUserAuthStubs {

  def isNotLoggedIn = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withHeader("WWW-Authenticate", """MDTP detail="BearerTokenExpired"""")
        .withStatus(401)))
    this
  }

  def isNotGGorPA = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "credentials":{
                     |    "providerId": "12345",
                     |    "providerType": "Verify"
                     |  },
                     |  "allEnrolments": []
                     |}
       """.stripMargin)))

    this
  }


}

trait ClientUserAuthStubs extends BasicUserAuthStubs {

  def givenCitizenDetails(nino: Nino, dob: String) = stubFor(
    get(urlEqualTo(s"/citizen-details/nino/$nino"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody(s"""{
                       |   "name": {
                       |      "current": {
                       |         "firstName": "John",
                       |         "lastName": "Smith"
                       |      },
                       |      "previous": []
                       |   },
                       |   "ids": {
                       |      "nino": "$nino"
                       |   },
                       |   "dateOfBirth": "$dob"
                       |}""".stripMargin)))

  def getAgencyEmailViaClient(arn: Arn) = stubFor(get(urlEqualTo(s"/agent-services-account/client/agency-email/${arn.value}"))
    .willReturn(
      aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "agencyEmail" : "agent@email.com"
                     |}""".stripMargin)))

  def givenClientAll(mtdItId: MtdItId, vrn: Vrn, nino: Nino, utr: Utr, cgtRef: CgtRef) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "optionalCredentials":{
                     |    "providerId": "12345",
                     |    "providerType": "GovernmentGateway"
                     |  },
                     |  "allEnrolments": [
                     |    {
                     |      "key": "HMRC-MTD-IT",
                     |      "identifiers": [
                     |        {
                     |          "key": "MTDITID",
                     |          "value": "${mtdItId.value}"
                     |        }
                     |      ],
                     |      "state": "Activated"
                     |    },
                     |    {
                     |      "key": "HMRC-MTD-VAT",
                     |      "identifiers": [
                     |        {
                     |          "key": "VRN",
                     |          "value": "${vrn.value}"
                     |        }
                     |      ],
                     |      "state": "Activated"
                     |    },
                     |    {
                     |      "key": "HMRC-NI",
                     |      "identifiers": [
                     |        {
                     |          "key": "NINO",
                     |          "value": "${nino.value}"
                     |        }
                     |      ],
                     |      "state": "Activated"
                     |    },
                     |    {
                     |      "key": "HMRC-TERS-ORG",
                     |      "identifiers": [
                     |        {
                     |          "key": "SAUTR",
                     |          "value": "${utr.value}"
                     |        }
                     |      ],
                     |      "state": "Activated"
                     |    },
                     |    {
                     |      "key":"HMRC-CGT-PD",
                     |      "identifiers": [
                     |        {
                     |          "key":"CGTPDRef",
                     |          "value":"${cgtRef.value}"
                     |        }
                     |      ]
                     |    }
                     |  ]
                     |}
       """.stripMargin)))

    this
  }

  def givenClientMtdItId(mtdItId: MtdItId) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "optionalCredentials":{
                     |    "providerId": "12345",
                     |    "providerType": "GovernmentGateway"
                     |  },
                     |  "allEnrolments": [
                     |    {
                     |      "key": "HMRC-MTD-IT",
                     |      "identifiers": [
                     |        {
                     |          "key": "MTDITID",
                     |          "value": "${mtdItId.value}"
                     |        }
                     |      ],
                     |      "state": "Activated"
                     |    }
                     |  ]
                     |}
       """.stripMargin)))

    this
  }

  def givenClientNi(nino: Nino) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     | "optionalCredentials":{
                     |    "providerId": "12345",
                     |    "providerType": "GovernmentGateway"
                     |  },
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

  def givenClientVat(vrn: Vrn) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse().withStatus(200)
        .withBody(s"""
                     |{
                     |  "optionalCredentials":{
                     |    "providerId": "12345",
                     |    "providerType": "GovernmentGateway"
                     |  },
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


  def givenClientTrust(utr: Utr) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse().withStatus(200)
        .withBody(s"""
                     |{
                     |  "optionalCredentials":{
                     |    "providerId": "12345",
                     |    "providerType": "GovernmentGateway"
                     |  },
                     |  "allEnrolments": [
                     |    {
                     |      "key": "HMRC-TERS-ORG",
                     |      "identifiers": [
                     |        {
                     |          "key": "SAUTR",
                     |          "value": "${utr.value}"
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

trait AgentAuthStubs extends BasicUserAuthStubs {

  protected var saAgentReference: Option[SaAgentReference] = None

  def givenGetAgentName(arn: Arn) = {
    stubFor(get(urlPathEqualTo("/agent-services-account/agent/agency-name"))
      .willReturn(aResponse().withStatus(200).withBody(
        s"""{
           |  "agencyName" : "My Agency"
           |}""".stripMargin)))
  }

  def givenAuthorisedAsAgent(arn: Arn) = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
                     |{
                     |  "affinityGroup": "Agent",
                     |  "allEnrolments": [
                     |    {
                     |      "key": "HMRC-AS-AGENT",
                     |      "identifiers": [
                     |        {
                     |          "key": "AgentReferenceNumber",
                     |          "value": "${arn.value}"
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

  def givenAgentNotSubscribed = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise"))
      .willReturn(aResponse()
        .withStatus(200)
        .withBody(s"""
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

trait StrideAuthStubs extends BasicUserAuthStubs{

  def givenUserIsAuthenticatedWithStride(strideRole: String, strideUserId: String): StrideAuthStubs = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                   |{
                   |"allEnrolments": [{
                   |  "key": "$strideRole"
                   |	}],
                   |  "optionalCredentials": {
                   |    "providerId": "$strideUserId",
                   |    "providerType": "PrivilegedApplication"
                   |  }
                   |}
       """.stripMargin)))
    this
  }
}