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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL

import javax.inject.{Inject, Named, Singleton}
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.joda.time.LocalDate
import play.api.libs.json.Json.reads
import play.api.libs.json.{JsObject, Reads, __}
import play.api.libs.json.Reads._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpReads}
import uk.gov.hmrc.http.logging.Authorization

case class BusinessDetails(businessData: Array[BusinessData], mtdbsa: Option[MtdItId])

case class BusinessData(businessAddressDetails: BusinessAddressDetails)

case class BusinessAddressDetails(countryCode: String, postalCode: Option[String])

object BusinessDetails {
  implicit val businessAddressDetailsReads: Reads[BusinessAddressDetails] = reads[BusinessAddressDetails]
  implicit val businessDataReads: Reads[BusinessData] = reads[BusinessData]
  implicit val businessDetailsReads: Reads[BusinessDetails] = reads[BusinessDetails]
}

case class VatCustomerInfo(effectiveRegistrationDate: Option[LocalDate])
object VatCustomerInfo {
  implicit val vatCustomerInfoReads: Reads[VatCustomerInfo] = {
    (__ \ "approvedInformation").readNullable[JsObject].map {
      case Some(approvedInformation) =>
        val maybeDate =
          (approvedInformation \ "customerDetails" \ "effectiveRegistrationDate").asOpt[String].map(LocalDate.parse)
        VatCustomerInfo(maybeDate)
      case None =>
        VatCustomerInfo(None)
    }
  }
}

@Singleton
class DesConnector @Inject()(
  @Named("des-baseUrl") baseUrl: URL,
  @Named("des.authorizationToken") authorizationToken: String,
  @Named("des.environment") environment: String,
  httpGet: HttpGet,
  metrics: Metrics)
    extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getBusinessDetails(
    nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]] =
    getWithDesHeaders[BusinessDetails](
      "getRegistrationBusinessDetailsByNino",
      new URL(baseUrl, s"/registration/business-details/nino/${encodePathSegment(nino.value)}").toString)

  def getVatCustomerInformation(
    vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerInfo]] = {
    val url = new URL(baseUrl, s"/vat/customer/vrn/${encodePathSegment(vrn.value)}/information")
    getWithDesHeaders[VatCustomerInfo]("GetVatCustomerInformation", url.toString)
  }

  private def getWithDesHeaders[T: HttpReads](apiName: String, url: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[T]] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpGet.GET[Option[T]](url)(implicitly[HttpReads[Option[T]]], desHeaderCarrier, ec)
    }
  }
}
