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

package uk.gov.hmrc.agentclientauthorisation.service

import javax.inject.{Inject, Singleton}
import org.joda.time.LocalDate
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentmtdidentifiers.model.{Utr, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KnownFactsCheckService @Inject()(
  desConnector: DesConnector,
  citizenDetailsConnector: CitizenDetailsConnector) {

  def clientVatRegistrationDateMatches(clientVrn: Vrn, suppliedVatRegistrationDate: LocalDate)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[Boolean]] =
    desConnector.getVatCustomerInformation(clientVrn).map {
      case Some(VatCustomerInfo(Some(effectiveRegistrationDate)))
          if effectiveRegistrationDate == suppliedVatRegistrationDate =>
        Some(true)
      case Some(VatCustomerInfo(Some(_))) =>
        Some(false)
      case Some(VatCustomerInfo(None)) =>
        None
      case None =>
        None
    }

  def clientDateOfBirthMatches(clientNino: Nino, suppliedDob: LocalDate)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext) =
    citizenDetailsConnector.getCitizenDateOfBirth(clientNino).map {
      case Some(CitizenDateOfBirth(Some(clientDob))) if clientDob == suppliedDob => Some(true)
      case Some(CitizenDateOfBirth(_))                                           => Some(false)
      case None                                                                  => None
    }
}
