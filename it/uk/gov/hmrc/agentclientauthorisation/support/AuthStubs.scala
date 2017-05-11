/*
 * Copyright 2017 HM Revenue & Customs
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
import play.api.http.HeaderNames
import uk.gov.hmrc.domain.{Nino, SaAgentReference}

trait WiremockAware {
  def wiremockBaseUrl: String
}

trait BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def isNotLoggedIn(): A = {
    // /authorise/... response is forwarded in AuthorisationFilter as is (if status is 401 or 403), which does not have
    // Content-length by default. That kills Play's WS lib, which is used in tests. (Somehow it works in the filter though.)
    stubFor(get(urlPathMatching(s"/authorise/read/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathMatching(s"/authorise/write/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathEqualTo(s"/auth/authority")).willReturn(aResponse().withStatus(401)))
    this
  }
}

trait UnknownUserAuthStubs[A] extends BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def oid: String

  def isLoggedIn(): A = {
    stubFor(get(urlPathMatching(s"/authorise/read/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathMatching(s"/authorise/write/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathEqualTo(s"/auth/authority")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "new-session":"/auth/oid/$oid/session",
         |  "enrolments":"/auth/oid/$oid/enrolments",
         |  "uri":"/auth/oid/$oid",
         |  "loggedInAt":"2016-06-20T10:44:29.634Z",
         |  "credentials":{
         |    "gatewayId":"0000001234567890"
         |  },
         |  "accounts":{
         |  },
         |  "lastUpdated":"2016-06-20T10:44:29.634Z",
         |  "credentialStrength":"strong",
         |  "confidenceLevel":50,
         |  "userDetailsLink":"$wiremockBaseUrl/user-details/id/$oid",
         |  "levelOfAssurance":"1",
         |  "previouslyLoggedInAt":"2016-06-20T09:48:37.112Z"
         |}
       """.stripMargin
    )))
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |[]
         """.stripMargin
    )))
    this
  }
}

trait ClientUserAuthStubs[A] extends BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def oid: String
  def clientId: Nino

  def isLoggedIn(): A = {
    stubFor(get(urlPathMatching(s"/authorise/read/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathMatching(s"/authorise/write/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathEqualTo(s"/auth/authority")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "new-session":"/auth/oid/$oid/session",
         |  "enrolments":"/auth/oid/$oid/enrolments",
         |  "uri":"/auth/oid/$oid",
         |  "loggedInAt":"2016-06-20T10:44:29.634Z",
         |  "credentials":{
         |    "gatewayId":"0000001592621267"
         |  },
         |  "nino": "${clientId.value}",
         |  "lastUpdated":"2016-06-20T10:44:29.634Z",
         |  "credentialStrength":"strong",
         |  "confidenceLevel":50,
         |  "userDetailsLink":"$wiremockBaseUrl/user-details/id/$oid",
         |  "levelOfAssurance":"1",
         |  "previouslyLoggedInAt":"2016-06-20T09:48:37.112Z"
         |}
       """.stripMargin
    )))
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |[]
         """.stripMargin
    )))

    this
  }

  def isLoggedInWithSessionId(): A = {
    stubFor(get(urlPathMatching(s"/authorise/read/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathMatching(s"/authorise/write/agent/.*")).willReturn(aResponse().withStatus(401).withHeader(HeaderNames.CONTENT_LENGTH, "0")))
    stubFor(get(urlPathEqualTo(s"/auth/authority")).withHeader("X-Session-ID", containing(clientId.value)).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "new-session":"/auth/oid/$oid/session",
         |  "enrolments":"/auth/oid/$oid/enrolments",
         |  "uri":"/auth/oid/$oid",
         |  "loggedInAt":"2016-06-20T10:44:29.634Z",
         |  "credentials":{
         |    "gatewayId":"0000001592621267"
         |  },
         |  "nino": "${clientId.value}",
         |  "lastUpdated":"2016-06-20T10:44:29.634Z",
         |  "credentialStrength":"strong",
         |  "confidenceLevel":50,
         |  "userDetailsLink":"$wiremockBaseUrl/user-details/id/$oid",
         |  "levelOfAssurance":"1",
         |  "previouslyLoggedInAt":"2016-06-20T09:48:37.112Z"
         |}
       """.stripMargin
    )))
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |[]
         """.stripMargin
    )))

    this
  }
}

object EnrolmentStates {
  val pending = "Pending"
  val activated = "Activated"
}

trait AgentAuthStubs[A] extends BasicUserAuthStubs[A] {
  me: A with WiremockAware =>

  def oid: String
  def arn: String
  def agentCode: String

  protected var saAgentReference: Option[SaAgentReference] = None


  def andIsSubscribedToAgentServices(): A = {
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |[{"key":"IR-PAYE-AGENT","identifiers":[{"key":"IrAgentReference","value":"HZ1234"}],"state":"Activated"},
         | {"key":"HMRC-AGENT-AGENT","identifiers":[{"key":"AgentRefNumber","value":"JARN1234567"}],"state":"Activated"},
         | {"key":"HMRC-AS-AGENT","identifiers":[{"key":"AnotherIdentifier", "value": "not the ARN"}, {"key":"AgentReferenceNumber","value":"${arn}"}],"state":"Activated"}]
         """.stripMargin
    )))
    this
  }

  def andIsNotSubscribedToAgentServices(): A = {
    stubFor(get(urlPathEqualTo(s"/auth/oid/$oid/enrolments")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |[{"key":"IR-PAYE-AGENT","identifiers":[{"key":"IrAgentReference","value":"HZ1234"}],"state":"Activated"},
         | {"key":"HMRC-AGENT-AGENT","identifiers":[{"key":"AgentRefNumber","value":"JARN1234567"}],"state":"Activated"}]
         """.stripMargin
    )))
    this
  }


  def isLoggedIn(): A = {
    stubFor(get(urlPathEqualTo(s"/authorise/read/agent/$agentCode")).willReturn(aResponse().withStatus(200)))
    stubFor(get(urlPathEqualTo(s"/authorise/write/agent/$agentCode")).willReturn(aResponse().withStatus(200)))
    stubFor(get(urlPathEqualTo(s"/auth/authority")).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "new-session":"/auth/oid/$oid/session",
         |  "enrolments":"/auth/oid/$oid/enrolments",
         |  "uri":"/auth/oid/$oid",
         |  "loggedInAt":"2016-06-20T10:44:29.634Z",
         |  "credentials":{
         |    "gatewayId":"0000001592621267"
         |  },
         |  "accounts":{
         |    "agent":{
         |      "link":"/agent/$agentCode",
         |      "agentCode":"$agentCode",
         |      "agentUserId":"ZMOQ1hrrP-9ZmnFw0kIA5vlc-mo",
         |      "agentUserRole":"admin",
         |      "payeReference":"HZ1234",
         |      "agentBusinessUtr":"JARN1234567"
         |    },
         |    "taxsAgent":{
         |      "link":"/taxsagent/V3264H",
         |      "uar":"V3264H"
         |    }
         |  },
         |  "lastUpdated":"2016-06-20T10:44:29.634Z",
         |  "credentialStrength":"strong",
         |  "confidenceLevel":50,
         |  "userDetailsLink":"$wiremockBaseUrl/user-details/id/$oid",
         |  "levelOfAssurance":"1",
         |  "previouslyLoggedInAt":"2016-06-20T09:48:37.112Z"
         |}
       """.stripMargin
    )))
    this
  }

  def isLoggedInWithSessionId(): A = {
    stubFor(get(urlPathEqualTo(s"/authorise/read/agent/$agentCode")).willReturn(aResponse().withStatus(200)))
    stubFor(get(urlPathEqualTo(s"/authorise/write/agent/$agentCode")).willReturn(aResponse().withStatus(200)))
    stubFor(get(urlPathEqualTo(s"/auth/authority")).withHeader("X-Session-ID", containing(arn)).willReturn(aResponse().withStatus(200).withBody(
      s"""
         |{
         |  "new-session":"/auth/oid/$oid/session",
         |  "enrolments":"/auth/oid/$oid/enrolments",
         |  "uri":"/auth/oid/$oid",
         |  "loggedInAt":"2016-06-20T10:44:29.634Z",
         |  "credentials":{
         |    "gatewayId":"0000001592621267"
         |  },
         |  "accounts":{
         |    "agent":{
         |      "link":"/agent/$agentCode",
         |      "agentCode":"$agentCode",
         |      "agentUserId":"ZMOQ1hrrP-9ZmnFw0kIA5vlc-mo",
         |      "agentUserRole":"admin",
         |      "payeReference":"HZ1234",
         |      "agentBusinessUtr":"JARN1234567"
         |    },
         |    "taxsAgent":{
         |      "link":"/taxsagent/V3264H",
         |      "uar":"V3264H"
         |    }
         |  },
         |  "lastUpdated":"2016-06-20T10:44:29.634Z",
         |  "credentialStrength":"strong",
         |  "confidenceLevel":50,
         |  "userDetailsLink":"$wiremockBaseUrl/user-details/id/$oid",
         |  "levelOfAssurance":"1",
         |  "previouslyLoggedInAt":"2016-06-20T09:48:37.112Z"
         |}
       """.stripMargin
    )))
    this
  }
}
