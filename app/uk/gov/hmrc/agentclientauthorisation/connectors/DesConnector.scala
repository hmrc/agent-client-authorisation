/*
 * Copyright 2023 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.Reads._
import play.api.libs.json.Writes
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.AgentCacheProvider
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {

  def getVatDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatDetails]]

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse]

  def getAgencyDetails(agentIdentifier: Either[Utr, Arn])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]]

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]]

}

@Singleton
class DesConnectorImpl @Inject()(
  appConfig: AppConfig,
  agentCacheProvider: AgentCacheProvider,
  httpClient: HttpClient,
  metrics: Metrics,
  desIfHeaders: DesIfHeaders)
    extends HttpAPIMonitor with DesConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl: String = appConfig.desBaseUrl

  def getVatDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatDetails]] = {
    val url = new URL(s"$baseUrl/vat/customer/vrn/${encodePathSegment(vrn.value)}/information")
    getWithDesHeaders("GetVatCustomerInformation", url, viaIF = false).map { response =>
      response.status match {
        case OK => response.json.asOpt[VatDetails]
        case NOT_FOUND =>
          logger.info(s"${response.status} response for getVatDetails ${response.body}")
          None
        case other =>
          throw UpstreamErrorResponse(response.body, other, other)
      }
    }
  }

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse] = {

    val url = new URL(s"$baseUrl/subscriptions/CGT/ZCGT/${cgtRef.value}")

    getWithDesHeaders("getCgtSubscription", url, viaIF = false).map { response =>
      val result = response.status match {
        case 200 =>
          Right(response.json.as[CgtSubscription])
        case _ =>
          (response.json \ "failures").asOpt[Seq[DesError]] match {
            case Some(e) => Left(CgtError(response.status, e))
            case None    => Left(CgtError(response.status, Seq(response.json.as[DesError])))
          }
      }

      CgtSubscriptionResponse(result)
    }
  }

  def getAgencyDetails(
    agentIdentifier: Either[Utr, Arn])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] = {
    val agentIdValue = agentIdentifier.fold(_.value, _.value)
    agentCacheProvider
      .agencyDetailsCache(agentIdValue) {
        getWithDesHeaders("getAgencyDetails", getAgentRecordUrl(agentIdentifier), viaIF = false).map { response =>
          response.status match {
            case status if is2xx(status) => response.json.asOpt[AgentDetailsDesResponse]
            case status if is4xx(status) => None
            case _ if response.body.contains("AGENT_TERMINATED") =>
              logger.warn(s"Discovered a Termination for agent: $agentIdValue")
              None
            case other =>
              throw UpstreamErrorResponse(s"unexpected response during 'getAgencyDetails', status: $other, response: ${response.body}", other, other)
          }
        }
      }
  }

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    val url = new URL(s"$baseUrl/registration/individual/utr/${UriEncoding.encodePathSegment(utr.value, "UTF-8")}")
    val headersConfig = desIfHeaders.headersConfig(viaIF = false, apiName = None, hc = hc, isInternalHost(url))

    monitor("ConsumedAPI-DES-GetAgentRegistration-POST") {

      httpClient
        .POST[DesRegistrationRequest, HttpResponse](url, body = DesRegistrationRequest(isAnAgent = false), headers = headersConfig.explicitHeaders)(
          implicitly[Writes[DesRegistrationRequest]],
          implicitly[HttpReads[HttpResponse]],
          headersConfig.hc,
          ec
        )
        .map { response =>
          response.status match {
            case status if is2xx(status) =>
              val isIndividual = (response.json \ "isAnIndividual").as[Boolean]
              if (isIndividual) {
                (response.json \ "individual").asOpt[Individual].flatMap(_.name)
              } else {
                (response.json \ "organisation" \ "organisationName").asOpt[String]
              }

            case other =>
              throw UpstreamErrorResponse(s"unexpected error during 'getBusinessName', statusCode=$other", other, other)
          }
        }
    }
  }

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] = {
    val url = new URL(s"$baseUrl/vat/customer/vrn/${UriEncoding.encodePathSegment(vrn.value, "UTF-8")}/information")
    agentCacheProvider.vatCustomerDetailsCache(vrn.value) {
      getWithDesHeaders("GetVatOrganisationNameByVrn", url, viaIF = false).map { response =>
        response.status match {
          case status if is2xx(status) =>
            (response.json \ "approvedInformation" \ "customerDetails").asOpt[VatCustomerDetails]
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getVatCustomerDetails ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getVatCustomerDetails', statusCode=$other", other, other)
        }
      }
    }
  }

  private def isInternalHost(url: URL): Boolean =
    appConfig.internalHostPatterns.exists(_.pattern.matcher(url.getHost).matches())

  private def getWithDesHeaders(apiName: String, url: URL, viaIF: Boolean)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[HttpResponse] = {
    val headersConfig = desIfHeaders.headersConfig(viaIF, Some(apiName), hc, isInternalHost(url))
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpClient
        .GET[HttpResponse](url, headers = headersConfig.explicitHeaders)(implicitly[HttpReads[HttpResponse]], headersConfig.hc, ec)
    }
  }

  private def getAgentRecordUrl(agentId: Either[Utr, Arn]): URL =
    agentId match {
      case Right(Arn(arn)) =>
        val encodedArn = UriEncoding.encodePathSegment(arn, "UTF-8")
        new URL(s"$baseUrl/registration/personal-details/arn/$encodedArn")
      case Left(Utr(utr)) =>
        val encodedUtr = UriEncoding.encodePathSegment(utr, "UTF-8")
        new URL(s"$baseUrl/registration/personal-details/utr/$encodedUtr")
    }
}
