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

trait DesStubs[A] {
  me: A with WiremockAware =>

  def clientId: Nino

  def hasABusinessPartnerRecord(postcode: String = "AA11AA", countryCode: String = "GB"): A = {
    stubFor(get(urlEqualTo(s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
      .withHeader("authorization", equalTo("Bearer secret"))
      .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
            .withBody(s"""
              |{
              |  "businessAddressDetails": {
              |    "postalCode": "$postcode",
              |    "countryCode": "$countryCode"
              |  }
              |}
              """.stripMargin)))
    this
  }

  def hasABusinessPartnerRecordWithMtdItId(postcode: String = "AA11AA", countryCode: String = "GB", mtdItId: String = "0123456789"): A = {
    stubFor(get(urlEqualTo(s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
      .withHeader("authorization", equalTo("Bearer secret"))
      .withHeader("environment", equalTo("test"))
        .willReturn(aResponse()
          .withStatus(200)
            .withBody(s"""
                         |{
                         |  "businessAddressDetails": {
                         |    "postalCode": "$postcode",
                         |    "countryCode": "$countryCode"
                         |  },
                         |  "mtdbsa": "$mtdItId"
                         |}
                         |              """.stripMargin)))
    this
  }

  def hasNoBusinessPartnerRecord: A = {
    stubFor(get(urlEqualTo(s"/registration/business-details/nino/${encodePathSegment(clientId.value)}"))
    .withHeader("authorization", equalTo("Bearer secret"))
    .withHeader("environment", equalTo("test"))
      .willReturn(aResponse()
        .withStatus(404)))

    this
  }
}
