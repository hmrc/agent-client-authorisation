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
import org.scalatest.concurrent.Eventually.eventually
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation

trait EmailStub {

  me: StartAndStopWireMock =>

  def givenEmailSent(emailInformation: EmailInformation) = {
    val emailInformationJson = Json.toJson(emailInformation).toString()

    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .withRequestBody(similarToJson(emailInformationJson))
        .willReturn(aResponse().withStatus(202))
    )

  }

  def givenEmailReturns500 =
    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .willReturn(aResponse().withStatus(500))
    )

  def verifyEmailRequestWasSent(times: Int): Unit =
    eventually {
      verify(
        times,
        postRequestedFor(urlPathEqualTo("/hmrc/email"))
      )
    }

  def verifyEmailRequestWasSentWithEmailInformation(times: Int, emailInformation: EmailInformation): Unit = {
    val emailInformationJson = Json.toJson(emailInformation).toString()
    eventually {
      verify(
        times,
        postRequestedFor(urlPathEqualTo("/hmrc/email"))
          .withRequestBody(similarToJson(emailInformationJson))
      )
    }
  }
  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}
