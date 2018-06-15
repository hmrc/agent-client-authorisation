/*
 * Copyright 2018 HM Revenue & Customs
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

import java.time.LocalDate
import javax.inject.{ Inject, Singleton }

import uk.gov.hmrc.agentclientauthorisation.connectors.{ DesConnector, VatCustomerInfo }
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.postcodeFormatInvalid
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ ExecutionContext, Future }

@Singleton
class KnownFactsCheckService @Inject() (desConnector: DesConnector) {

  def clientVatRegistrationDateMatches(clientVrn: Vrn, suppliedVatRegistrationDate: LocalDate)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Boolean]] = {

    desConnector.getVatCustomerInformation(clientVrn).map {
      case Some(VatCustomerInfo(Some(effectiveRegistrationDate))) if effectiveRegistrationDate == suppliedVatRegistrationDate =>
        Some(true)
      case Some(VatCustomerInfo(_)) =>
        Some(false)
      case None =>
        None
    }
  }
}

