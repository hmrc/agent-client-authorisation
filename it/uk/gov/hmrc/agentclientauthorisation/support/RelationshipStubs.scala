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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.Nino

trait RelationshipStubs[A] {
  me: A with WiremockAware =>
  def canonicalClientId: MtdItId

  def aRelationshipIsCreatedWith(arn: Arn): A = {
    stubFor(put(urlEqualTo(s"/agent-client-relationships/agent/${arn.value}/service/HMRC-MTD-IT/client/MTDITID/${canonicalClientId.value}"))
      .willReturn(aResponse().withStatus(201)))
    this
  }

  def anAfiRelationshipIsCreatedWith(arn: Arn, nino: Nino): A = {
    stubFor(put(urlEqualTo(s"/agent-fi-relationship/relationships/agent/${arn.value}/service/PERSONAL-INCOME-RECORD/client/${nino.value}"))
      .willReturn(aResponse().withStatus(201)))
    this
  }
}
