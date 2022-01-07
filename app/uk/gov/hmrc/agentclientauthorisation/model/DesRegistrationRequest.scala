/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json, OFormat}

case class Individual(firstName: Option[String], lastName: Option[String]) {
  def name: Option[String] =
    for {
      fn <- firstName
      ln <- lastName
    } yield s"$fn $ln"
}

object Individual {
  implicit val individualFormat: OFormat[Individual] = Json.format
}

case class DesRegistrationRequest(requiresNameMatch: Boolean = false, regime: String = "ITSA", isAnAgent: Boolean)

object DesRegistrationRequest {
  implicit val formats: Format[DesRegistrationRequest] = Json.format[DesRegistrationRequest]
}

case class DesRegistrationResponse(isAnIndividual: Boolean, organisationName: Option[String], individual: Option[Individual])

object DesRegistrationResponse {
  implicit val responseFormat: OFormat[DesRegistrationResponse] = Json.format
}
