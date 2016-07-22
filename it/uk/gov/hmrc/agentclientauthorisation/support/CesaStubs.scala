/*
 * Copyright 2016 HM Revenue & Customs
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

import uk.gov.hmrc.domain.SaUtr
import com.github.tomakehurst.wiremock.client.WireMock._

object CesaStubs {
  def saTaxpayerExists(saUtr: SaUtr) = {
    stubFor(get(urlEqualTo(s"/self-assessment/individual/$saUtr/designatory-details/taxpayer"))
      .willReturn(
        aResponse()
          .withStatus(200)
          .withBody("""{
            |    "address": {
            |        "postcode": "AA1 1AA"
            |    },
            |    "name": {
            |        "forename": "First",
            |        "surname": "Last",
            |        "title": "Mr"
            |    }
            |}""".stripMargin)))
  }
}
