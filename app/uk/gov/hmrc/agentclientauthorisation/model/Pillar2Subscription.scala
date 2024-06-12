/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

case class UpeDetails(
  customerIdentification1: Option[String] = None,
  customerIdentification2: Option[String] = None,
  organisationName: String,
  registrationDate: String,
  domesticOnly: Boolean = false,
  filingMember: Boolean = false
)
case object UpeDetails {
  implicit val format: Format[UpeDetails] = Json.format[UpeDetails]
}

case class AccountingPeriod(startDate: String, endDate: String, dueDate: Option[String] = None)
case object AccountingPeriod {
  implicit val format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

case class UpeCorrespAddressDetails(
  addressLine1: String,
  addressLine2: Option[String] = None,
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  postCode: Option[String] = None,
  countryCode: String
)
case object UpeCorrespAddressDetails {
  implicit val format: Format[UpeCorrespAddressDetails] = Json.format[UpeCorrespAddressDetails]
}

case class ContactDetails(name: String, telephone: Option[String] = None, emailAddress: String)
case object ContactDetails {
  implicit val format: Format[ContactDetails] = Json.format[ContactDetails]
}

case class FilingMemberDetails(
  safeId: String,
  customerIdentification1: Option[String] = None,
  customerIdentification2: Option[String] = None,
  organisationName: String
)
case object FilingMemberDetails {
  implicit val format: Format[FilingMemberDetails] = Json.format[FilingMemberDetails]
}

case class AccountStatus(inactive: Boolean = false)
case object AccountStatus {
  implicit val format: Format[AccountStatus] = Json.format[AccountStatus]
}

case class Pillar2Record(
  formBundleNumber: String,
  upeDetails: UpeDetails,
  accountingPeriod: AccountingPeriod,
  upeCorrespAddressDetails: UpeCorrespAddressDetails,
  primaryContactDetails: ContactDetails,
  secondaryContactDetails: Option[ContactDetails] = None,
  filingMemberDetails: Option[FilingMemberDetails] = None,
  accountStatus: Option[AccountStatus] = None,
  id: Option[String] = None)
case object Pillar2Record {
  implicit val format: Format[Pillar2Record] = Json.format[Pillar2Record]
}

case class Pillar2Subscription(organisationName: String, registrationDate: LocalDate)

object Pillar2Subscription {
  implicit val format: Format[Pillar2Subscription] = Json.format[Pillar2Subscription]
  val localDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd") //2023-12-31

  def fromPillar2Record(pillar2Record: Pillar2Record): Pillar2Subscription =
    Pillar2Subscription(pillar2Record.upeDetails.organisationName, LocalDate.parse(pillar2Record.upeDetails.registrationDate, localDateFormatter))
}

case class Pillar2Error(httpResponseCode: Int, errors: Seq[DesError])

object Pillar2Error {
  implicit val format: Format[Pillar2Error] = Json.format[Pillar2Error]
}

case class Pillar2SubscriptionResponse(response: Either[Pillar2Error, Pillar2Subscription])
