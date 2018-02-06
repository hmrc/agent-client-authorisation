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
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier

trait RelationshipStubs[A] {
  me: A with WiremockAware =>
  def canonicalClientId: TaxIdentifier

  def anMtdItRelationshipIsCreatedWith(arn: Arn): A = {
    stubFor(put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-IT/client/MTDITID/${canonicalClientId.value}"))
      .willReturn(aResponse().withStatus(201)))
    this
  }

  def anAfiRelationshipIsCreatedWith(arn: Arn, clientId: ClientId): A = {
    stubFor(put(urlEqualTo(s"/agent-fi-relationship/relationships/agent/${arn.value}/service/PERSONAL-INCOME-RECORD/client/${clientId.value}"))
      .willReturn(aResponse().withStatus(201)))
    this
  }

  def anMtdVatRelationshipIsCreatedWith(arn: Arn, clientId: TaxIdentifier): A = {
    stubFor(put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-VAT/client/VRN/${clientId.value}"))
      .willReturn(aResponse().withStatus(201)))
    this
  }

  def verifyCallToCreateMtdVatRelationship(arn: Arn, clientId: TaxIdentifier) = {
    verify(1,
      putRequestedFor(
        urlPathEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-VAT/client/VRN/${clientId.value}")))
  }

  def verifyNoCallsToCreateMtdVatRelationship = {
    verify(0,
      putRequestedFor(
        urlPathEqualTo(s"/agent-client-relationships/agent/[^/]+/service/[^/]+/client/[^/]+/[^/]+")))
  }
}
