/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Service}

import java.time.LocalDate

case class InvitationInfo(
  invitationId: InvitationId,
  expiryDate: LocalDate,
  status: InvitationStatus,
  arn: Arn,
  service: Service,
  isRelationshipEnded: Boolean = false,
  relationshipEndedBy: Option[String] = None,
  events: List[StatusChangeEvent],
  isAltItsa: Boolean = false,
  fromAcr: Boolean = false
)

object InvitationInfo {
  implicit val format: OFormat[InvitationInfo] = Json.format[InvitationInfo]
}
