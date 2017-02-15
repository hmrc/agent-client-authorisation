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

import uk.gov.hmrc.domain.Nino
import com.github.tomakehurst.wiremock.client.WireMock._
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment

trait EtmpStubs[A] {
  me: A with WiremockAware =>

  def nino: Option[Nino]

  def hasABusinessPartnerRecord: A = {
    stubFor(get(urlEqualTo(s"/registration/business-details/nino/${encodePathSegment(nino.get.value)}"))
      .withHeader("authorization", equalTo("Bearer secret"))
      .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
            .withBody("""
              |{
              |  "businessAddressDetails": {
              |    "postalCode": "AA11AA",
              |    "countryCode": "GB"
              |  }
              |}
              """.stripMargin)))
    this
  }

  def hasNoBusinessPartnerRecord: A = {
    stubFor(get(urlEqualTo(s"/registration/business-details/nino/${encodePathSegment(nino.get.value)}"))
    .withHeader("authorization", equalTo("Bearer secret"))
    .withHeader("environment", equalTo("test"))
      .willReturn(aResponse()
        .withStatus(404)))

    this
  }
}
