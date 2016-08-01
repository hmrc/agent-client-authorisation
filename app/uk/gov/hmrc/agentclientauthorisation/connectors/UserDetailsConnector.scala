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

package uk.gov.hmrc.agentclientauthorisation.connectors

import play.api.libs.json.Json
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

case class UserDetails(name: String, lastName: Option[String], agentFriendlyName: Option[String]) {

  val agentName: String = {
    (name, lastName) match {
      case (_, Some(_)) => s"$name ${lastName.get}"
      case (_, None) => name
    }
  }
}

object UserDetails {
  lazy implicit val formats = Json.format[UserDetails]
}

class UserDetailsConnector(http: HttpGet) {

  def userDetails(url: String)(implicit hc: HeaderCarrier): Future[UserDetails] = {
    http.GET[UserDetails](url)
  }
}
