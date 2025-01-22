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

import play.api.libs.json.{Format, JsError, JsResult, JsString, JsSuccess, JsValue}

sealed trait InvitationStatus {

  def toEither: Either[String, InvitationStatus] = this match {
    case Unknown(status) => Left(status)
    case status          => Right(status)
  }

  def leftMap[X](f: String => X): Either[X, InvitationStatus] =
    toEither.left.map(f)

  override def toString: String = InvitationStatus.unapply(this).getOrElse("Unknown")
}

case object Pending extends InvitationStatus

case object Expired extends InvitationStatus

case object Rejected extends InvitationStatus

case object Accepted extends InvitationStatus

case object Cancelled extends InvitationStatus

case object DeAuthorised extends InvitationStatus

case object PartialAuth extends InvitationStatus

case class Unknown(attempted: String) extends InvitationStatus

object InvitationStatus {
  def unapply(status: InvitationStatus): Option[String] = status match {
    case Pending      => Some("Pending")
    case Rejected     => Some("Rejected")
    case Accepted     => Some("Accepted")
    case Cancelled    => Some("Cancelled")
    case Expired      => Some("Expired")
    case DeAuthorised => Some("Deauthorised")
    case PartialAuth  => Some("Partialauth")
    case _            => None
  }

  def apply(status: String): InvitationStatus = status.toLowerCase match {
    case "pending"      => Pending
    case "rejected"     => Rejected
    case "accepted"     => Accepted
    case "cancelled"    => Cancelled
    case "expired"      => Expired
    case "deauthorised" => DeAuthorised
    case "partialauth"  => PartialAuth
    case _              => Unknown(status)
  }

  implicit val invitationStatusFormat: Format[InvitationStatus] = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = apply(json.as[String]) match {
      case Unknown(value) => JsError(s"Status of [$value] is not a valid InvitationStatus")
      case value          => JsSuccess(value)
    }

    override def writes(o: InvitationStatus): JsValue =
      unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }
}
