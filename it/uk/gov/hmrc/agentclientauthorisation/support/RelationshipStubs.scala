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

trait RelationshipStubs[A] {
  me: A with WiremockAware =>
  def canonicalClientId: MtdItId

  def aRelationshipIsCreatedWith(arn: Arn): A = {
    stubFor(put(urlEqualTo(s"/agent-client-relationships/relationships/mtd-sa/${canonicalClientId.value}/${arn.value}"))
      .willReturn(aResponse().withStatus(201)))
    this
  }
}
