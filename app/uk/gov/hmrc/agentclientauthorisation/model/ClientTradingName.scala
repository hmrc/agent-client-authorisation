/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.libs.json.Json

case class CustomerDetails(
  organisationName: Option[String],
  individual: Option[Individual],
  tradingName: Option[String])

case class Individual(
  title: Option[String],
  firstName: Option[String],
  middleName: Option[String],
  lastName: Option[String]) {

  def name: String =
    Seq(title, firstName, middleName, lastName).flatten.map(_.trim).filter(_.nonEmpty).mkString(" ")
}

object Individual {
  implicit val format = Json.format[Individual]
}

object CustomerDetails {
  implicit val format = Json.format[CustomerDetails]
}
