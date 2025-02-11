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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.Logging

sealed trait InvitationStatusAction

object InvitationStatusAction extends Logging {
  case object Accept extends InvitationStatusAction

  case object Cancel extends InvitationStatusAction

  case object Reject extends InvitationStatusAction

  def apply(status: String): InvitationStatusAction = status.toLowerCase match {
    case "accept" => Accept
    case "cancel" => Cancel
    case "reject" => Reject
    case value =>
      logger.warn(s"Action of [$value] is not a valid status change action")
      throw new IllegalArgumentException
  }

  def fromInvitationStatus(invitationStatus: InvitationStatus): InvitationStatusAction = invitationStatus match {
    case Rejected    => Reject
    case Accepted    => Accept
    case PartialAuth => Accept
    case Cancelled   => Cancel
    case value =>
      logger.warn(s"Action of [$value] is not a valid status change action")
      throw new IllegalArgumentException

  }

  def unapply(status: InvitationStatusAction): String = status match {
    case Accept => "accept"
    case Cancel => "cancel"
    case Reject => "reject"
  }
}
