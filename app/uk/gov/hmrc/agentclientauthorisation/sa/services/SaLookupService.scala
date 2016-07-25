/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.sa.services

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.agentclientauthorisation.sa.connectors.{CesaIndividualsConnector, CesaTaxpayer}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

class SaLookupService(cesaIndividualsConnector: CesaIndividualsConnector) {
  def lookupByUtrAndPostcode(saUtr: SaUtr, postcode: String)(implicit hc: HeaderCarrier): Future[Option[String]] =
  {
    cesaIndividualsConnector.taxpayer(saUtr).map { maybeTaxpayer: Option[CesaTaxpayer] =>
      maybeTaxpayer match {
        case Some(taxPayer) if postcodeMatches(taxPayer.address.postcode, postcode)
        => Some(name(taxPayer))
        case _
        => None
      }
    }
  }

  def postcodeMatches(postcode: Option[String], toCheck: String) = {
    postcode.flatMap(value => Some(value.toLowerCase.replaceAll(" ", "") == toCheck.toLowerCase.replaceAll(" ", "")))
            .getOrElse(false)
  }

  def name(cesaTaxpayer: CesaTaxpayer): String = cesaTaxpayer.name.toString
}
