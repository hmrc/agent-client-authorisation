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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.libs.json.Json
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

case class EnrolmentIdentifier(key: String, value: String)
case class AuthEnrolment(key: String, identifiers: Seq[EnrolmentIdentifier], state: String) {
  def identifier(key: String): Option[String] = identifiers.find(_.key == key).map(_.value)
}

object AuthEnrolment {
  implicit val idformat = Json.format[EnrolmentIdentifier]
  implicit val format = Json.format[AuthEnrolment]
}

case class Enrolments(enrolments: Set[AuthEnrolment]) {

  def arnOption: Option[Arn] = asAgentEnrolment.flatMap(_.identifier("AgentReferenceNumber")).map(Arn.apply)

  private def asAgentEnrolment: Option[AuthEnrolment] = getEnrolment("HMRC-AS-AGENT")

  private def getEnrolment(key: String): Option[AuthEnrolment] = enrolments.find(e => e.key == key)
}

object Enrolments {
  implicit val formats = Json.format[Enrolments]
}
