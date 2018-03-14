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

import javax.inject.{ Inject, Singleton }

import play.api.mvc.Result
import uk.gov.hmrc.agentclientauthorisation.connectors.{ BusinessDetails, DesConnector }
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService.normalise
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ ExecutionContext, Future }
import uk.gov.hmrc.http.HeaderCarrier

@Singleton
class PostcodeService @Inject() (desConnector: DesConnector) {
  def clientPostcodeMatches(clientIdentifier: String, postcode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Result]] = {
    // we can use head here due to the guard on the array size in the case statement but these are not a general purpose functions
    def postcodeMatches(details: BusinessDetails, postcode: String) =
      details.businessData.head.businessAddressDetails.postalCode.map(normalise).contains(normalise(postcode))

    def isUkAddress(details: BusinessDetails) =
      details.businessData.head.businessAddressDetails.countryCode.toUpperCase == "GB"

    desConnector.getBusinessDetails(Nino(clientIdentifier)).map {
      case Some(details) if details.businessData.length != 1 => Some(PostcodeDoesNotMatch)
      case Some(details) if postcodeMatches(details, postcode) && isUkAddress(details) => None
      case Some(details) if postcodeMatches(details, postcode) => Some(nonUkAddress(details.businessData.head.businessAddressDetails.countryCode))
      case Some(_) => Some(PostcodeDoesNotMatch)
      case None => Some(ClientRegistrationNotFound)
    }
  }
}

object PostcodeService {
  def normalise(postcode: String): String = postcode.replaceAll("\\s", "").toUpperCase
}
