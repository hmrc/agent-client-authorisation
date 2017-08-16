/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL
import javax.inject.{Inject, Named, Singleton}

import play.api.libs.json.Json.reads
import play.api.libs.json.Reads
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}

import scala.concurrent.{ExecutionContext, Future}

case class BusinessDetails(businessAddressDetails: AddressDetails, mtdbsa: Option[MtdItId])

case class AddressDetails(countryCode: String, postalCode: Option[String])

object BusinessDetails {
  implicit val addressReads: Reads[AddressDetails] = reads[AddressDetails]
  implicit val businessDetailsReads: Reads[BusinessDetails] = reads[BusinessDetails]
}

@Singleton
class DesConnector @Inject()(@Named("des-baseUrl") baseUrl: URL,
                             @Named("des.authorizationToken") authorizationToken: String,
                             @Named("des.environment") environment: String,
                             httpGet: HttpGet) {

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]] =
    getWithDesHeaders(new URL(baseUrl, s"/registration/business-details/nino/${encodePathSegment(nino.value)}").toString)


  private def getWithDesHeaders(url: String)(implicit hc: HeaderCarrier): Future[Option[BusinessDetails]] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
    httpGet.GET[Option[BusinessDetails]](url)(implicitly[HttpReads[Option[BusinessDetails]]], desHeaderCarrier)
  }
}
