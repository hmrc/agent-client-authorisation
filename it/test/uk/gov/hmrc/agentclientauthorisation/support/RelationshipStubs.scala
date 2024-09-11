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
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.TaxIdentifier

trait RelationshipStubs {

  def deleteMtdItRelationshipIsCreatedWith(arn: Arn, mtdItId: ClientId, itsaServiceName: String) = {
    stubFor(
      delete(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${mtdItId.value}"))
        .willReturn(aResponse().withStatus(201))
    )
    this
  }

  def getMtdItRelationshipIsCreatedWith(arn: Arn, mtdItId: ClientId, itsaServiceName: String) = {
    stubFor(
      get(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${mtdItId.value}"))
        .willReturn(aResponse().withStatus(201))
    )
    this
  }

  def getMtdItRelationshipDoNotExists(arn: Arn, mtdItId: ClientId, itsaServiceName: String) = {
    stubFor(
      get(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${mtdItId.value}"))
        .willReturn(aResponse().withStatus(404))
    )
    this
  }

  def anMtdItRelationshipIsCreatedWith(arn: Arn, mtdItId: MtdItId) = {
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-IT/client/MTDITID/${mtdItId.value}"))
        .willReturn(aResponse().withStatus(201))
    )
    this
  }

  def anAfiRelationshipIsCreatedWith(arn: Arn, clientId: ClientId) = {
    stubFor(
      put(urlEqualTo(s"/agent-fi-relationship/relationships/agent/${arn.value}/service/PERSONAL-INCOME-RECORD/client/${clientId.value}"))
        .willReturn(aResponse().withStatus(201))
    )
    this
  }

  def anAfiRelationshipIsCreatedFails(arn: Arn, clientId: ClientId) = {
    stubFor(
      put(urlEqualTo(s"/agent-fi-relationship/relationships/agent/${arn.value}/service/PERSONAL-INCOME-RECORD/client/${clientId.value}"))
        .willReturn(aResponse().withStatus(502))
    )
    this
  }

  def anMtdVatRelationshipIsCreatedWith(arn: Arn, clientId: TaxIdentifier) = {
    stubFor(
      put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-VAT/client/VRN/${clientId.value}"))
        .willReturn(aResponse().withStatus(201))
    )
    this
  }

  def verifyCallToGetMtdItRelationship(arn: Arn, clientId: TaxIdentifier, itsaServiceName: String) =
    verify(
      1,
      getRequestedFor(urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${clientId.value}"))
    )

  def verifyNoCallsToGetMtdItRelationship(arn: Arn, clientId: TaxIdentifier, itsaServiceName: String) =
    verify(
      0,
      deleteRequestedFor(urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${clientId.value}"))
    )

  def verifyCallToDeleteMtdItRelationship(arn: Arn, clientId: TaxIdentifier, itsaServiceName: String) =
    verify(
      1,
      deleteRequestedFor(urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${clientId.value}"))
    )

  def verifyNoCallsToDeletetMtdItRelationship(arn: Arn, clientId: TaxIdentifier, itsaServiceName: String) =
    verify(
      0,
      getRequestedFor(urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/$itsaServiceName/client/MTDITID/${clientId.value}"))
    )

  def verifyCallToCreateMtdVatRelationship(arn: Arn, clientId: TaxIdentifier) =
    verify(1, putRequestedFor(urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-VAT/client/VRN/${clientId.value}")))

  def verifyNoCallsToCreateMtdVatRelationship(arn: Arn, clientId: TaxIdentifier) =
    verify(0, putRequestedFor(urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-VAT/client/VRN/${clientId.value}")))
}
