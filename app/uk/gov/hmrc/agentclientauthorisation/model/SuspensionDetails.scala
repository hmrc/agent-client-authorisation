/*
 * Copyright 2020 HM Revenue & Customs
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

import play.api.libs.json.{Json, OFormat}
import Service._

case class SuspensionDetails(suspensionStatus: Boolean, regimes: Option[Set[String]]) {

  //PERSONAL-INCOME-RECORD service has no enrolment / regime so cannot be suspended
  private val validSuspensionRegimes = Set("ITSA", "VATC", "TRS", "CGT")

  val suspendedRegimes: Set[String] =
    this.regimes.fold(Set.empty[String])(rs => if (rs.contains("ALL")) validSuspensionRegimes else rs)

  private val serviceToRegime: Map[Service, String] =
    Map(MtdIt -> "ITSA", Vat -> "VATC", Trust -> "TRS", CapitalGains -> "CGT", PersonalIncomeRecord -> "PIR")

  def isRegimeSuspended(service: Service): Boolean = {
    val regime = serviceToRegime(service)
    suspendedRegimes.contains(regime)
  }

}

object SuspensionDetails {
  implicit val formats: OFormat[SuspensionDetails] = Json.format

  val notSuspended = SuspensionDetails(suspensionStatus = false, None)
}
