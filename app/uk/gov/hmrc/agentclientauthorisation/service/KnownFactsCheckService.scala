/*
 * Copyright 2021 HM Revenue & Customs
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

import org.joda.time.LocalDate
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.model.{VatDetails, VatKnownFactCheckResult}
import uk.gov.hmrc.agentclientauthorisation.model.VatKnownFactCheckResult._
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class KnownFactsCheckService @Inject()(desConnector: DesConnector, citizenDetailsConnector: CitizenDetailsConnector) {

  def clientVatRegistrationCheckResult(clientVrn: Vrn, suppliedVatRegistrationDate: LocalDate)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[VatKnownFactCheckResult] =
    desConnector.getVatDetails(clientVrn).map {
      case Some(VatDetails(Some(regDate), isInsolvent)) if regDate == suppliedVatRegistrationDate =>
        if (!isInsolvent) VatKnownFactCheckOk else VatRecordClientInsolvent
      case Some(VatDetails(Some(regDate), _)) => VatKnownFactNotMatched
      case _                                  => VatDetailsNotFound
    }

  def clientDateOfBirthMatches(clientNino: Nino, suppliedDob: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] =
    citizenDetailsConnector.getCitizenDateOfBirth(clientNino).map {
      case Some(CitizenDateOfBirth(Some(clientDob))) if clientDob == suppliedDob => Some(true)
      case Some(CitizenDateOfBirth(_))                                           => Some(false)
      case None                                                                  => None
    }
}
