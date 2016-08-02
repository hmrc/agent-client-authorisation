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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.{JsObject, Json}

trait UserDetailsStub extends StubUtils {
  me: StartAndStopWireMock =>

  def userExists(user: BaseUser): Unit = {

    val lastNameJson: JsObject = user.lastName.map(n => Json.obj("lastName" -> n)).getOrElse(Json.obj())

    val agentFriendlyNameJson = (user match {
      case agentAdmin: AgentAdmin =>
        agentAdmin.agentFriendlyName.map { n =>
          Json.obj("agentFriendlyName" -> n)
        }
      case _ => None
    }).getOrElse(Json.obj())

    val bodyJson =
      lastNameJson ++
      agentFriendlyNameJson ++
      Json.obj(
        "name" -> user.name,
        "email" -> "agentassistant@example.com")


    stubFor(get(urlPathEqualTo(userDetailsPath(user)))
      .willReturn(aResponse()
        .withStatus(200)
        .withHeader("Content-Type", "application/json")
        .withBody(bodyJson.toString)
      )
    )
  }

  def userDetailsUrl(user: BaseUser): String =
    user.wiremockBaseUrl + userDetailsPath(user)

  private def userDetailsPath(user: BaseUser): String = {
    s"/user-details/id/${user.oid}"
  }
}
