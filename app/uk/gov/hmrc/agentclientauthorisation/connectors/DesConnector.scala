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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.util.UUID

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.Logging
import play.api.libs.json.Reads._
import play.api.libs.json.Writes
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.AgentCacheProvider
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]]

  def getVatRegDate(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatRegDate]]

  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse]

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse]

  def getAgencyDetails(agentIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]]

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getNinoFor(mtdbsa: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]]

  def getMtdIdFor(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]]

  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]]
}

@Singleton
class DesConnectorImpl @Inject()(appConfig: AppConfig, agentCacheProvider: AgentCacheProvider, httpClient: HttpClient, metrics: Metrics)
    extends HttpAPIMonitor with DesConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val desEnvironment: String = appConfig.desEnvironment
  private val desAuthorizationToken: String = appConfig.desAuthToken
  private val baseUrl: String = appConfig.desBaseUrl

  private val ifEnvironment: String = appConfig.ifEnvironment
  private val ifAuthorizationToken: String = appConfig.ifAuthToken

  private val Environment = "Environment"
  private val CorrelationId = "CorrelationId"
  private val Authorization = "Authorization"

  private def outboundHeaders(viaIF: Boolean) =
    Seq(
      Environment   -> s"${if (viaIF) { ifEnvironment } else { desEnvironment }}",
      CorrelationId -> UUID.randomUUID().toString,
      Authorization -> s"Bearer ${if (viaIF) { ifAuthorizationToken } else { desAuthorizationToken }}"
    )

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]] = {
    val url = s"$baseUrl/registration/business-details/nino/${encodePathSegment(nino.value)}"
    getWithDesIfHeaders("getRegistrationBusinessDetailsByNino", url).map { response =>
      response.status match {
        case status if is2xx(status) => response.json.asOpt[BusinessDetails]
        case status if is4xx(status) =>
          logger.warn(s"4xx response for getBusinessDetails ${response.body}")
          None
        case other =>
          throw UpstreamErrorResponse(s"unexpected error during 'getBusinessDetails', statusCode=$other", other, other)
      }
    }
  }

  def getVatRegDate(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatRegDate]] = {
    val url = s"$baseUrl/vat/customer/vrn/${encodePathSegment(vrn.value)}/information"
    getWithDesIfHeaders("GetVatCustomerInformation", url).map { response =>
      response.status match {
        case status if is2xx(status) => response.json.asOpt[VatRegDate]
        case status if is4xx(status) =>
          logger.warn(s"4xx response for getVatRegDate ${response.body}")
          None
        case other =>
          throw UpstreamErrorResponse(s"unexpected error during 'getVatRegDate', statusCode=$other", other, other)
      }
    }
  }

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

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse] = {

    val url = s"$baseUrl/subscriptions/CGT/ZCGT/${cgtRef.value}"

    getWithDesIfHeaders("getCgtSubscription", url).map { response =>
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

  def getAgencyDetails(agentIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] =
    agentCacheProvider
      .agencyDetailsCache(agentIdentifier.value) {
        getWithDesIfHeaders("getAgencyDetails", getAgentRecordUrl(agentIdentifier)).map { response =>
          response.status match {
            case status if is2xx(status) => response.json.asOpt[AgentDetailsDesResponse]
            case status if is4xx(status) => None
            case _ if response.body.contains("AGENT_TERMINATED") =>
              logger.warn(s"Discovered a Termination for agent: $agentIdentifier")
              None
            case other =>
              throw UpstreamErrorResponse(s"unexpected response during 'getAgencyDetails', status: $other, response: ${response.body}", other, other)
          }
        }
      }

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor("ConsumedAPI-DES-GetAgentRegistration-POST") {
      val url = s"$baseUrl/registration/individual/utr/${UriEncoding.encodePathSegment(utr.value, "UTF-8")}"

      httpClient
        .POST[DesRegistrationRequest, HttpResponse](url, body = DesRegistrationRequest(isAnAgent = false), headers = outboundHeaders(false))(
          implicitly[Writes[DesRegistrationRequest]],
          implicitly[HttpReads[HttpResponse]],
          hc,
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

  def getNinoFor(mtdbsa: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]] = {
    val url = s"$baseUrl/registration/business-details/mtdbsa/${UriEncoding.encodePathSegment(mtdbsa.value, "UTF-8")}"
    agentCacheProvider.clientNinoCache(mtdbsa.value) {
      getWithDesIfHeaders("GetRegistrationBusinessDetailsByMtdbsa", url).map { response =>
        response.status match {
          case status if is2xx(status) => response.json.asOpt[NinoDesResponse].map(_.nino)
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getNinoFor ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getNinoFor', statusCode=$other", other, other)
        }
      }
    }
  }

  def getMtdIdFor(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]] = {
    val url = s"$baseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.clientMtdItIdCache(nino.value) {
      getWithDesIfHeaders("GetRegistrationBusinessDetailsByNino", url).map { response =>
        response.status match {
          case status if is2xx(status) => response.json.asOpt[MtdItIdDesResponse].map(_.mtdbsa)
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getMtdItIdFor ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getMtdIdFor', statusCode=$other", other, other)
        }
      }
    }
  }

  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    val url =
      s"$baseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.tradingNameCache(nino.value) {
      getWithDesIfHeaders("GetTradingNameByNino", url).map { response =>
        response.status match {
          case status if is2xx(status) => ((response.json \ "businessData")(0) \ "tradingName").asOpt[String]
          case status if is4xx(status) =>
            logger.warn(s"4xx response from getTradingNameForNino ${response.body}")
            None
          case other =>
            throw UpstreamErrorResponse(s"unexpected error during 'getTradingNameForNino', statusCode=$other", other, other)
        }
      }
    }
  }

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] = {
    val url = s"$baseUrl/vat/customer/vrn/${UriEncoding.encodePathSegment(vrn.value, "UTF-8")}/information"
    agentCacheProvider.vatCustomerDetailsCache(vrn.value) {
      getWithDesIfHeaders("GetVatOrganisationNameByVrn", url).map { response =>
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

  private def getWithDesIfHeaders(apiName: String, url: String, viaIf: Boolean = false)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HttpResponse] =
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpClient.GET[HttpResponse](url, headers = outboundHeaders(viaIf))(implicitly[HttpReads[HttpResponse]], hc, ec)
    }

  private def getAgentRecordUrl(agentId: TaxIdentifier) =
    agentId match {
      case Arn(arn) =>
        val encodedArn = UriEncoding.encodePathSegment(arn, "UTF-8")
        s"$baseUrl/registration/personal-details/arn/$encodedArn"
      case Utr(utr) =>
        val encodedUtr = UriEncoding.encodePathSegment(utr, "UTF-8")
        s"$baseUrl/registration/personal-details/utr/$encodedUtr"
      case _ =>
        throw new Exception(s"The client identifier $agentId is not supported.")
    }

  private val utrPattern = "^\\d{10}$"

  private def getTrustNameUrl(trustTaxIdentifier: String, ifEnabled: Boolean): String =
    if (!ifEnabled) s"$baseUrl/trusts/agent-known-fact-check/$trustTaxIdentifier"
    else if (trustTaxIdentifier.matches(utrPattern))
      s"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/UTR/$trustTaxIdentifier"
    else s"${appConfig.ifPlatformBaseUrl}/trusts/agent-known-fact-check/URN/$trustTaxIdentifier"
}
