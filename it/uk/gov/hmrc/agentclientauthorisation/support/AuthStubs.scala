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
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import play.api.test.FakeRequest
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, SaAgentReference}
import uk.gov.hmrc.http.{HeaderCarrier, SessionKeys}

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

  def givenUnauthorisedForUnsupportedAuthProvider =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"UnsupportedAuthProvider\"")))

  def givenUnauthorisedForUnsupportedAffinityGroup =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(401)
            .withHeader("WWW-Authenticate", "MDTP detail=\"UnsupportedAffinityGroup\"")))

}

trait ClientUserAuthStubs extends BasicUserAuthStubs {


  def givenClientAll(mtdItId: MtdItId, vrn: Vrn, nino: Nino, utr: Utr, urn: Urn, cgtRef: CgtRef) = {
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
                     |      "key": "HMRC-TERSNT-ORG",
                     |      "identifiers": [
                     |        {
                     |          "key": "URN",
                     |          "value": "${urn.value}"
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
                     |      ],
                     |      "state": "Activated"
                     |    }
                     |  ]
                     |}
       """.stripMargin)))

    this
  }

  def givenClientAllBusCgt(cgtRef: CgtRef) = {
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
                     |      "key":"HMRC-CGT-PD",
                     |      "identifiers": [
                     |        {
                     |          "key":"CGTPDRef",
                     |          "value":"${cgtRef.value}"
                     |        }
                     |      ],
                     |      "state": "Activated"
                     |    }
                     |  ]
                     |}
       """.stripMargin)))
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

  def isLoggedIn = {
    stubFor(post(urlPathEqualTo(s"/auth/authorise")).willReturn(aResponse().withStatus(200).withBody("{}")))
    this
  }

  def authorisedAsValidClientWithAffinityGroup[A](request: FakeRequest[A], clientVatEnrolKey: String, clientITEnrolKey: String)(implicit hc: HeaderCarrier): FakeRequest[A] =
    authenticatedClientAffinityGroup(request, clientVatEnrolKey, clientITEnrolKey)

  def authorisedAsNinoClient[A](request: FakeRequest[A], nino: Nino)(implicit hc: HeaderCarrier): FakeRequest[A] = {
    givenAuthorisedFor(
      payload =
        """
          |{
          |"authorise" : [ {
          | "authProviders" : [ "GovernmentGateway" ]
          |}, {
          |"$or" : [ {
          | "affinityGroup" : "Individual"
          |},{
          | "affinityGroup" : "Organisation"
          |}]
          |}],
          |"retrieve" : [ "affinityGroup", "confidenceLevel",
          |"allEnrolments" ]
          | }""".stripMargin,
      responseBody =
        s"""
           |{
           |"affinityGroup" : "Individual",
           |"confidenceLevel" : 200,
           |"allEnrolments":
           |[
           |  {
           |    "key" : "HMRC-NI",
           |    "identifiers" : [
           |    {"key":"NINO", "value": "${nino.value}"}
           |    ]
           |  }
           |]
           |}""".stripMargin
    )
    request.withSession(
      SessionKeys.authToken -> "Bearer XYZ",
      SessionKeys.sessionId -> hc.sessionId.map(_.value).getOrElse("ClientSession123456")
    )
  }

  def authenticatedClientAffinityGroup[A](request: FakeRequest[A], vatEnrolKey: String, itEnrolKey: String, confidenceLevel: Int = 200)(implicit hc: HeaderCarrier): FakeRequest[A] = {
    givenAuthorisedFor(
      payload = """
        |{
        |"authorise" : [ {
        | "authProviders" : [ "GovernmentGateway" ]
        |}, {
        |"$or" : [ {
        | "affinityGroup" : "Individual"
        |},{
        | "affinityGroup" : "Organisation"
        |}]
        |}],
        |"retrieve" : [ "affinityGroup", "confidenceLevel",
        |"allEnrolments" ]
        | }""".stripMargin,
      responseBody = s"""
         |{
         |"affinityGroup" : "Individual",
         |"confidenceLevel" : $confidenceLevel,
         |"allEnrolments":
         |[
         |  {
         |    "key" : "$vatEnrolKey",
         |    "identifiers": [
         |      {"key":"VRN", "value": "101747696"}
         |      ]
         |  },
         |  {
         |    "key" : "$itEnrolKey",
         |    "identifiers" : [
         |    {"key":"MTDITID", "value": "ABCDEF123456789"}
         |    ]
         |  },
         |  {
         |    "key" : "HMRC-CGT-PD",
         |    "identifiers" : [
         |    {"key":"CGTPDRef", "value": "XMCGTP123456789"}
         |    ]
         |  },
         |  {
         |    "key" : "HMRC-NI",
         |    "identifiers" : [
         |    {"key":"NINO", "value": "AB123456A"}
         |    ]
         |  }
         |]
         |}""".stripMargin
    )
    request.withSession(
      SessionKeys.authToken -> "Bearer XYZ",
      SessionKeys.sessionId -> hc.sessionId.map(_.value).getOrElse("ClientSession123456")
    )
  }

  def authorisedAsValidClient[A](request: FakeRequest[A], mtdItId: String): FakeRequest[A] =
    authenticatedClient(request)

  def authorisedAsValidAgent[A](request: FakeRequest[A], arn: String): FakeRequest[A] =
    authenticatedAgent(request, Enrolment("HMRC-AS-AGENT", "AgentReferenceNumber", arn))

  def authenticatedClient[A](request: FakeRequest[A]): FakeRequest[A] = {
    givenAuthorisedFor(
      """
        |{"authorise" : [ {
        |"$or" : [ {
        |"identifiers" : [ ],
        |"state" : "Activated",
        |"enrolment" : "HMRC-MTD-IT"
        |}, {
        |"identifiers" : [ ],
        |"state" : "Activated",
        |"enrolment" : "HMRC-NI"
        |}, {
        |"identifiers" : [ ],
        |"state" : "Activated",
        |"enrolment" : "HMRC-MTD-VAT"
        |} ]},
        | {"authProviders" :
        | [ "GovernmentGateway" ]
        | } ],
        | "retrieve" : [ ]
        | }""".stripMargin,
      "{}"
    )
    request.withSession(SessionKeys.authToken -> "Bearer XYZ")
  }

  case class Enrolment(serviceName: String, identifierName: String, identifierValue: String)

  def authenticatedAgent[A](request: FakeRequest[A], enrolment: Enrolment): FakeRequest[A] = {
    givenAuthorisedFor(
      s"""
         |{
         |  "authorise": [
         |    { "identifiers":[], "state":"Activated", "enrolment": "${enrolment.serviceName}" },
         |    { "authProviders": ["GovernmentGateway"] }
         |  ],
         |  "retrieve":["authorisedEnrolments"]
         |}
           """.stripMargin,
      s"""
         |{
         |"authorisedEnrolments": [
         |  { "key":"${enrolment.serviceName}", "identifiers": [
         |    {"key":"${enrolment.identifierName}", "value": "${enrolment.identifierValue}"}
         |  ]}
         |]}
          """.stripMargin
    )
    request.withSession(SessionKeys.authToken -> "Bearer XYZ")
  }

  def givenAuthorisedFor(payload: String, responseBody: String): StubMapping =
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(payload, true, true))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(responseBody)))
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
                   |}""".stripMargin)))
    this
  }

  def givenUserIsAuthenticatedWithMultipleStrideRoles(strideRoles: Seq[String], strideUserId: String): StrideAuthStubs = {
    stubFor(
      post(urlPathEqualTo(s"/auth/authorise"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |"allEnrolments": [{
                         |  "key": "$strideRoles"
                         |	}],
                         |  "optionalCredentials": {
                         |    "providerId": "$strideUserId",
                         |    "providerType": "PrivilegedApplication"
                         |  }
                         |}""".stripMargin)))
    this
  }

  def givenOnlyStrideStub(strideRole: String, strideUserId: String): StrideAuthStubs = {
    stubFor(
      post(urlEqualTo("/auth/authorise"))
        .withRequestBody(equalToJson(s"""
                                        |{
                                        |  "authorise": [
                                        |    { "authProviders": ["PrivilegedApplication"] }
                                        |  ],
                                        |  "retrieve":["allEnrolments"]
                                        |}""".stripMargin,
          true, true))
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
                         |}""".stripMargin)
        ))
    this
  }
}