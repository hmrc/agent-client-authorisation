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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL
import java.util.UUID

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import org.joda.time.LocalDate
import play.api.libs.json.Json.reads
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, Reads, _}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{CgtRef, MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

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

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]]

  def getVatCustomerInformation(
    vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerInfo]]

  def getTrustName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse]

  def getCgtSubscription(
    cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse]
}

@Singleton
class DesConnectorImpl @Inject()(appConfig: AppConfig, httpGet: HttpClient, metrics: Metrics)
    extends HttpAPIMonitor with DesConnector {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val rawHttpReads = new RawHttpReads

  private val environment: String = appConfig.desEnvironment
  private val authorizationToken: String = appConfig.desAuthToken
  private val baseUrl: String = appConfig.desBaseUrl

  def getBusinessDetails(
    nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]] =
    getWithDesHeaders[BusinessDetails](
      "getRegistrationBusinessDetailsByNino",
      s"$baseUrl/registration/business-details/nino/${encodePathSegment(nino.value)}")

  def getVatCustomerInformation(
    vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerInfo]] = {
    val url = s"$baseUrl/vat/customer/vrn/${encodePathSegment(vrn.value)}/information"
    getWithDesHeaders[VatCustomerInfo]("GetVatCustomerInformation", url)
  }

  def getTrustName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] = {

    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment :+ "CorrelationId" -> UUID.randomUUID().toString
    )

    val url = s"$baseUrl/trusts/agent-known-fact-check/${utr.value}"

    httpGet.GET[HttpResponse](url)(rawHttpReads, desHeaderCarrier, ec).map { response =>
      response.status match {
        case 200 => TrustResponse(Right(TrustName((response.json \ "trustDetails" \ "trustName").as[String])))
        case 400 | 404 =>
          TrustResponse(Left(InvalidTrust((response.json \ "code").as[String], (response.json \ "reason").as[String])))
        case _ => throw new RuntimeException(s"unexpected status during retrieving TrustName, error=${response.body}")
      }
    }
  }

  def getCgtSubscription(
    cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse] = {

    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment :+ "CorrelationId" -> UUID.randomUUID().toString
    )

    val url = s"$baseUrl/subscriptions/CGT/ZCGT/${cgtRef.value}"

    httpGet.GET[HttpResponse](url)(rawHttpReads, desHeaderCarrier, ec).map { response =>
      val result = response.status match {
        case 200 =>
          Right(response.json.as[CgtSubscription])
        case 400 | 404 =>
          (response.json \ "failures").asOpt[Seq[DesError]] match {
            case Some(e) => Left(CgtError(response.status, e))
            case None    => Left(CgtError(response.status, Seq(response.json.as[DesError])))
          }
      }

      CgtSubscriptionResponse(result)
    }
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
