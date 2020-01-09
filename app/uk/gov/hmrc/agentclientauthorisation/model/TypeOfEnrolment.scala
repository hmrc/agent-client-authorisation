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

import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.Enrolment
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

sealed class TypeOfEnrolment(
  val enrolmentKey: String,
  val identifierKey: String,
  val identifierForValue: String => TaxIdentifier) {

  def extractIdentifierFrom(enrolments: Set[Enrolment]): Option[TaxIdentifier] = {
    val maybeEnrolment: Option[Enrolment] = enrolments.find(_.key equals enrolmentKey)

    maybeEnrolment
      .flatMap(_.identifiers.find(_.key equals identifierKey))
      .map(enrolmentIdentifier => identifierForValue(enrolmentIdentifier.value))
  }

  def enrolmentService: EnrolmentService = EnrolmentService(enrolmentKey)
}

case object EnrolmentAsAgent extends TypeOfEnrolment("HMRC-AS-AGENT", "AgentReferenceNumber", Arn.apply)
case object EnrolmentMtdIt extends TypeOfEnrolment("HMRC-MTD-IT", "MTDITID", MtdItId.apply)
case object EnrolmentMtdVat extends TypeOfEnrolment("HMRC-MTD-VAT", "VRN", Vrn.apply)
case object EnrolmentNino extends TypeOfEnrolment("HMRC-NI", "NINO", Nino.apply)
case object EnrolmentTrust extends TypeOfEnrolment("HMRC-TERS-ORG", "SAUTR", Utr.apply)
case object EnrolmentCgt extends TypeOfEnrolment("HMRC-CGT-PD", "CGTPDRef", CgtRef.apply)

object TypeOfEnrolment {

  def apply(identifier: TaxIdentifier): TypeOfEnrolment = identifier match {
    case Nino(_)    => EnrolmentNino
    case MtdItId(_) => EnrolmentMtdIt
    case Vrn(_)     => EnrolmentMtdVat
    case Arn(_)     => EnrolmentAsAgent
    case Utr(_)     => EnrolmentTrust
    case CgtRef(_)  => EnrolmentCgt
    case _          => throw new IllegalArgumentException(s"Unhandled TaxIdentifier type ${identifier.getClass.getName}")
  }
}

case class EnrolmentService(value: String)

object EnrolmentService {}

case class EnrolmentIdentifierValue(value: String) {

  def asMtdItId: MtdItId = MtdItId(value)
  def asVrn: Vrn = Vrn(value)
}
