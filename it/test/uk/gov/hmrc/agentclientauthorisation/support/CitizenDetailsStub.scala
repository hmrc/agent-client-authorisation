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

trait CitizenDetailsStub {
  me: StartAndStopWireMock =>

  def givenCitizenDetailsAreKnownFor(nino: String, dob: String, sautr: Option[String] = None): StubMapping =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |   "name": {
                         |      "current": {
                         |         "firstName": "John",
                         |         "lastName": "Smith"
                         |      },
                         |      "previous": []
                         |   },
                         |   "ids": {
                         |      "nino": "$nino" ${sautr.map(utr => s""","sautr": "$utr" """).getOrElse("")}
                         |   },
                         |   "dateOfBirth": "$dob"
                         |}""".stripMargin)
        )
    )

  def givenCitizenDetailsNoDob(nino: String): StubMapping =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |   "name": {
                         |      "current": {
                         |         "firstName": "John",
                         |         "lastName": "Smith"
                         |      },
                         |      "previous": []
                         |   },
                         |   "ids": {
                         |      "nino": "$nino"
                         |   }
                         |}""".stripMargin)
        )
    )

  def givenCitizenDetailsReturnsResponseFor(nino: String, response: Int): StubMapping =
    stubFor(
      get(urlEqualTo(s"/citizen-details/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(response)
        )
    )

  def givenDesignatoryDetailsAreKNownFor(nino: String, postcode: Option[String]): StubMapping =
    stubFor(
      get(urlEqualTo(s"/citizen-details/$nino/designatory-details"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |"person": {
                         |  "nino": "$nino"
                         |} ${postcode.map(pc => s""", "address": { "postcode": "$pc"} """).getOrElse("")}
                         |}""".stripMargin)
        )
    )
}
