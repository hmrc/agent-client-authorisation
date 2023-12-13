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
import play.api.libs.json.Reads._
import play.api.libs.json.JsValue
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.AgentCacheProvider
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@ImplementedBy(classOf[IfConnectorImpl])
trait IfConnector {
  // FF enabled, formerly DES
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse]

  //IF API#1171
  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]]

  //IF API#1171
  def getNinoFor(mtdId: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]]

  //IF API#1171
  def getMtdIdFor(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]]

  //IF API#1171
  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]]

  def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]]

}

@Singleton
class IfConnectorImpl @Inject()(
  appConfig: AppConfig,
  agentCacheProvider: AgentCacheProvider,
  httpClient: HttpClient,
  metrics: Metrics,
  desIfHeaders: DesIfHeaders)
    extends HttpAPIMonitor with IfConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val ifBaseUrl: String = appConfig.ifPlatformBaseUrl

  /* IF API#1171 Get Business Details (for ITSA customers) */
  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]] = {
    val url = s"$ifBaseUrl/registration/business-details/nino/${encodePathSegment(nino.value)}"
    getWithDesIfHeaders("GetRegistrationBusinessDetailsByNino", url, viaIf = true).map { response =>
      response.status match {
        case status if is2xx(status) => (response.json \ "taxPayerDisplayResponse").asOpt[BusinessDetails]
        case status if is4xx(status) =>
          logger.warn(s"4xx response for getBusinessDetails ${response.body}")
          None
        case other =>
          throw UpstreamErrorResponse(s"unexpected error during 'getBusinessDetails', statusCode=$other", other, other)
      }
    }
  }

  //IF API#1495 Agent Known Fact Check (Trusts)
  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] = {

    val url = getTrustNameUrl(trustTaxIdentifier, appConfig.desIFEnabled)

    getWithDesIfHeaders("getTrustName", url, appConfig.desIFEnabled).map { response =>
      response.status match {
        case status if is2xx(status) =>
          TrustResponse(Right(TrustName((response.json \ "trustDetails" \ "trustName").as[String])))
        case status if is4xx(status) =>
          val invalidTrust = Try(((response.json \ "code").as[String], (response.json \ "reason").as[String])).toEither
            .fold(ex => {
              logger.error(s"${response.body}, ${ex.getMessage}")
              InvalidTrust(status.toString, ex.getMessage)
            }, t => InvalidTrust(t._1, t._2))
          TrustResponse(Left(invalidTrust))
        case other =>
          throw UpstreamErrorResponse(s"unexpected status during retrieving TrustName, error=${response.body}", other, other)
      }
    }
  }

  /* Get Business Details (for ITSA customers) */
  def getNinoFor(mtdId: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]] = {
    val url = s"$ifBaseUrl/registration/business-details/mtdId/${UriEncoding.encodePathSegment(mtdId.value, "UTF-8")}"
    agentCacheProvider.clientNinoCache(mtdId.value) {
      getWithDesIfHeaders("GetRegistrationBusinessDetailsByMtdId", url, viaIf = true).map { response =>
        response.status match {
          case status if is2xx(status) => (response.json \ "taxPayerDisplayResponse").asOpt[NinoDesResponse].map(_.nino)
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getNinoFor ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getNinoFor', statusCode=$other", other, other)
        }
      }
    }
  }

  /* IF API#1171 Get Business Details (for ITSA customers) */
  def getMtdIdFor(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]] = {
    val url = s"$ifBaseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.clientMtdItIdCache(nino.value) {
      getWithDesIfHeaders("GetRegistrationBusinessDetailsByNino", url, viaIf = true).map { response =>
        response.status match {
          case status if is2xx(status) => (response.json \ "taxPayerDisplayResponse").asOpt[MtdItIdIfResponse].map(_.mtdId)
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getMtdItIdFor ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getMtdIdFor', statusCode=$other", other, other)
        }
      }
    }
  }

  /* IF API#1171 Get Business Details (for ITSA customers) */
  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    val url =
      s"$ifBaseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.tradingNameCache(nino.value) {
      getWithDesIfHeaders("GetTradingNameByNino", url, viaIf = true).map { response =>
        response.status match {
          case status if is2xx(status) =>
            (response.json \ "taxPayerDisplayResponse" \ "businessData").toOption.map(_(0) \ "tradingName").flatMap(_.asOpt[String])
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getTradingNameForNino ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getTradingNameForNino', statusCode=$other", other, other)
        }
      }
    }
  }

  //IF API#1712 PPT Subscription Display
  def getPptSubscriptionRawJson(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsValue]] = {
    val url = s"$ifBaseUrl/plastic-packaging-tax/subscriptions/PPT/${pptRef.value}/display"
    agentCacheProvider.pptSubscriptionCache(pptRef.value) {
      getWithDesIfHeaders("GetPptSubscriptionDisplay", url, viaIf = true).map { response =>
        response.status match {
          case status if is2xx(status) =>
            Some(response.json)
          case status if is4xx(status) =>
            logger.warn(s"$status response from getPptSubscriptionDisplay ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error from getPptSubscriptionDisplay, status = $other", other)
        }
      }
    }
  }

  //IF API#1712 PPT Subscription Display
  def getPptSubscription(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[PptSubscription]] =
    getPptSubscriptionRawJson(pptRef).map(_.flatMap(_.asOpt[PptSubscription](PptSubscription.reads)))

  private def getWithDesIfHeaders(apiName: String, url: String, viaIf: Boolean)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    monitor(s"ConsumedAPI-IF-$apiName-GET") {
      httpClient.GET[HttpResponse](url, headers = desIfHeaders.outboundHeaders(viaIf, Some(apiName)))(implicitly[HttpReads[HttpResponse]], hc, ec)
    }

  private val utrPattern = "^\\d{10}$"

  private def getTrustNameUrl(trustTaxIdentifier: String, ifEnabled: Boolean): String =
    if (!ifEnabled) s"${appConfig.desBaseUrl}/trusts/agent-known-fact-check/$trustTaxIdentifier"
    else if (trustTaxIdentifier.matches(utrPattern))
      s"$ifBaseUrl/trusts/agent-known-fact-check/UTR/$trustTaxIdentifier"
    else s"$ifBaseUrl/trusts/agent-known-fact-check/URN/$trustTaxIdentifier"
}
