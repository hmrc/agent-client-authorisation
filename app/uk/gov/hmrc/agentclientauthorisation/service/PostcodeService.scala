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

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.agentclientauthorisation.connectors.DesConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.BusinessDetails
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService.normalise
import uk.gov.hmrc.agentclientauthorisation.util.{failure, valueOps}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PostcodeService @Inject()(desConnector: DesConnector) {

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r

  def postCodeMatches(clientId: String, postcode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    if (postcode.isEmpty) failure(postcodeRequired("HMRC-MTD-IT"))
    else {
      postcodeWithoutSpacesRegex
        .findFirstIn(postcode)
        .map(_ => clientPostcodeMatches(clientId, postcode))
        .getOrElse(failure(postcodeFormatInvalid(s"""The submitted postcode, $postcode, does not match the expected format.""")))
    }

  private def clientPostcodeMatches(clientIdentifier: String, postcode: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    // we can use head here due to the guard on the array size in the case statement but these are not a general purpose functions
    def postcodeMatches(details: BusinessDetails, postcode: String): Boolean =
      details.businessData.head.businessAddressDetails
        .flatMap(_.postalCode.map(normalise))
        .contains(normalise(postcode))

    def isUkAddress(details: BusinessDetails) =
      details.businessData.head.businessAddressDetails.map(_.countryCode.toUpperCase).contains("GB")

    desConnector.getBusinessDetails(Nino(clientIdentifier)).flatMap {
      case Some(details) if details.businessData.length != 1                           => failure(PostcodeDoesNotMatch)
      case Some(details) if postcodeMatches(details, postcode) && isUkAddress(details) => ().toFuture
      case Some(details) if postcodeMatches(details, postcode) =>
        details.businessData.head.businessAddressDetails.map(_.countryCode).map(nonUkAddress).fold(().toFuture)(failure)
      case Some(_) => failure(PostcodeDoesNotMatch)
      case None    => failure(ClientRegistrationNotFound)
    }
  }
}

object PostcodeService {
  def normalise(postcode: String): String = postcode.replaceAll("\\s", "").toUpperCase
}
