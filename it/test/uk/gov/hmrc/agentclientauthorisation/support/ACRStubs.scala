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
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.{ChangeInvitationStatusRequest, Invitation}
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

  def givenACRChangeStatusSuccess(
    arn: Arn,
    service: String,
    clientId: String,
    changeInvitationStatusRequest: ChangeInvitationStatusRequest
  ): StubMapping = {
    val changeRequestJson = Json.toJson(changeInvitationStatusRequest).toString()
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/transitional/change-invitation-status/arn/${arn.value}/service/$service/client/$clientId"))
        .withRequestBody(similarToJson(changeRequestJson))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )
  }

  def givenACRChangeStatusNotFound(
    arn: Arn,
    service: String,
    clientId: String,
    changeInvitationStatusRequest: ChangeInvitationStatusRequest
  ): StubMapping = {
    val changeRequestJson = Json.toJson(changeInvitationStatusRequest).toString()
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/transitional/change-invitation-status/arn/${arn.value}/service/$service/client/$clientId"))
        .withRequestBody(similarToJson(changeRequestJson))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )
  }

  def givenACRChangeStatusBadRequest(
    arn: Arn,
    service: String,
    clientId: String
  ): StubMapping =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/transitional/change-invitation-status/arn/${arn.value}/service/$service/client/$clientId"))
        .willReturn(
          aResponse()
            .withStatus(400)
        )
    )

  def verifyACRChangeStatusSent(arn: Arn, service: String, clientId: String): Unit =
    eventually {
      verify(
        1,
        putRequestedFor(
          urlPathEqualTo(s"/agent-client-relationships/transitional/change-invitation-status/arn/${arn.value}/service/$service/client/$clientId")
        )
      )
    }

  def verifyACRChangeStatusWasNOTSent(arn: Arn, service: String, clientId: String): Unit =
    eventually {
      verify(
        0,
        putRequestedFor(
          urlPathEqualTo(s"/agent-client-relationships/transitional/change-invitation-status/arn/${arn.value}/service/$service/client/$clientId")
        )
      )
    }
  def givenACRChangeStatusByIdSuccess(invitationId: String, action: String): StubMapping =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/authorisation-request/action-invitation/$invitationId/action/$action"))
        .willReturn(
          aResponse()
            .withStatus(204)
        )
    )

  def givenACRChangeStatusByIdNotFound(invitationId: String, action: String): StubMapping =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/authorisation-request/action-invitation/$invitationId/action/$action"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )

  def givenACRChangeStatusByIdBadRequest(invitationId: String, action: String): StubMapping =
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/authorisation-request/action-invitation/$invitationId/action/$action"))
        .willReturn(
          aResponse()
            .withStatus(400)
        )
    )

  def verifyACRChangeStatusByIdSent(invitationId: String, action: String): Unit =
    eventually {
      verify(
        1,
        putRequestedFor(
          urlPathEqualTo(s"/agent-client-relationships/authorisation-request/action-invitation/$invitationId/action/$action")
        )
      )
    }

  def verifyACRChangeStatusByIdWasNOTSent(invitationId: String, action: String): Unit =
    eventually {
      verify(
        0,
        putRequestedFor(
          urlPathEqualTo(s"/agent-client-relationships/authorisation-request/action-invitation/$invitationId/action/$action")
        )
      )
    }

  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

  def stubUrnToUtrCall(urn: String, responseStatus: Int): StubMapping =
    stubFor(
      post(urlEqualTo(s"/agent-client-relationships/invitations/trusts-enrolment-orchestrator/$urn/update"))
        .willReturn(
          aResponse()
            .withStatus(responseStatus)
        )
    )

  def givenAcrInvitationFound(arn: Arn, invitationId: String, expectedInvitation: Invitation, withClientName: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-client-relationships/lookup-invitation/$invitationId"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |  "invitationId": "$invitationId",
                         |  "arn": "${arn.value}",
                         |  "service": "${expectedInvitation.service}",
                         |  "clientId": "${expectedInvitation.clientId.value}",
                         |  "clientIdType": "${expectedInvitation.clientId.typeId}",
                         |  "suppliedClientId": "${expectedInvitation.suppliedClientId.value}",
                         |  "suppliedClientIdType": "${expectedInvitation.suppliedClientId.typeId}",
                         |  "clientName": "$withClientName",
                         |  "status": "Pending",
                         |  "clientType": "${expectedInvitation.clientType.getOrElse("personal")}",
                         |  "expiryDate": "2025-03-31",
                         |  "created": "2025-03-10T00:00:00Z",
                         |  "lastUpdated": "2025-03-10T00:00:00Z"
                         |}""".stripMargin)
        )
    )

  def givenAcrInvitationNotFound(invitationId: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/agent-client-relationships/lookup-invitation/$invitationId"))
        .willReturn(
          aResponse()
            .withStatus(404)
        )
    )
}
