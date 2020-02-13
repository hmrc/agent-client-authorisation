/*
 * Copyright 2020 HM Revenue & Customs
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
import play.api.Logger
import play.api.libs.json.Reads._
import play.api.libs.json.{JsObject, _}
import play.utils.UriEncoding
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.AgentCacheProvider
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpReads, HttpResponse, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {

  def getBusinessDetails(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[BusinessDetails]]

  def getVatRegDate(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatRegDate]]

  def getTrustName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse]

  def getCgtSubscription(
    cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse]

  def getAgencyDetails(agentIdentifier: TaxIdentifier)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]]

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getNinoFor(mtdbsa: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]]

  def getMtdIdFor(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]]

  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getVatCustomerDetails(
    vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]]
}

@Singleton
class DesConnectorImpl @Inject()(
  appConfig: AppConfig,
  agentCacheProvider: AgentCacheProvider,
  httpClient: HttpClient,
  metrics: Metrics)
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

  def getVatRegDate(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatRegDate]] = {
    val url = s"$baseUrl/vat/customer/vrn/${encodePathSegment(vrn.value)}/information"
    getWithDesHeaders[VatRegDate]("GetVatCustomerInformation", url)
  }

  def getTrustName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[TrustResponse] = {

    val url = s"$baseUrl/trusts/agent-known-fact-check/${utr.value}"

    httpClient.GET[HttpResponse](url)(rawHttpReads, addDesHeaders, ec).map { response =>
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

    val url = s"$baseUrl/subscriptions/CGT/ZCGT/${cgtRef.value}"

    httpClient.GET[HttpResponse](url)(rawHttpReads, addDesHeaders, ec).map { response =>
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

  private def addDesHeaders(implicit hc: HeaderCarrier): HeaderCarrier =
    hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment :+ "CorrelationId" -> UUID.randomUUID().toString
    )

  def getAgencyDetails(agentIdentifier: TaxIdentifier)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] =
    agentCacheProvider
      .agencyDetailsCache(agentIdentifier.value) {
        getWithDesHeaders[AgentDetailsDesResponse]("GetAgentRecord", getAgentRecordUrl(agentIdentifier)).recover {
          case e if e.getMessage.contains("AGENT_TERMINATED") =>
            Logger(getClass).warn(s"Discovered a Termination: $e")
            None
        }
      }

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    for {
      responseOpt <- getRegistration(utr)
      response = responseOpt match {
        case Some(res) => res
        case None      => throw new NotFoundException("Registration record not found")
      }
    } yield if (response.isAnIndividual) response.individual.flatMap(_.name) else response.organisationName

  def getNinoFor(mtdbsa: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]] = {
    val url = s"$baseUrl/registration/business-details/mtdbsa/${UriEncoding.encodePathSegment(mtdbsa.value, "UTF-8")}"
    agentCacheProvider.clientNinoCache(mtdbsa.value) {
      getWithDesHeaders[NinoDesResponse]("GetRegistrationBusinessDetailsByMtdbsa", url)
        .map(_.map(_.nino))
    }
  }

  def getMtdIdFor(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]] = {
    val url = s"$baseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.clientMtdItIdCache(nino.value) {
      getWithDesHeaders[MtdItIdDesResponse]("GetRegistrationBusinessDetailsByNino", url)
        .map(_.map(_.mtdbsa))
    }
  }

  def getTradingNameForNino(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    val url =
      s"$baseUrl/registration/business-details/nino/${UriEncoding.encodePathSegment(nino.value, "UTF-8")}"
    agentCacheProvider.tradingNameCache(nino.value) {
      getWithDesHeaders[JsObject]("GetTradingNameByNino", url).map(
        obj =>
          obj.flatMap { o =>
            ((o \ "businessData")(0) \ "tradingName").asOpt[String]
        }
      )
    }
  }

  def getVatCustomerDetails(
    vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]] = {
    val url = s"$baseUrl/vat/customer/vrn/${UriEncoding.encodePathSegment(vrn.value, "UTF-8")}/information"
    agentCacheProvider.vatCustomerDetailsCache(vrn.value) {
      getWithDesHeaders[JsObject]("GetVatOrganisationNameByVrn", url)
        .map(
          obj =>
            obj.flatMap { o =>
              (o \ "approvedInformation" \ "customerDetails").asOpt[VatCustomerDetails]
          }
        )
    }
  }

  private def getWithDesHeaders[T: HttpReads](apiName: String, url: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[T]] = {
    val desHeaderCarrier = hc.copy(
      authorization = Some(Authorization(s"Bearer $authorizationToken")),
      extraHeaders = hc.extraHeaders :+ "Environment" -> environment)
    monitor(s"ConsumedAPI-DES-$apiName-GET") {
      httpClient.GET[Option[T]](url)(implicitly[HttpReads[Option[T]]], desHeaderCarrier, ec)
    }
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

  private def getRegistration(
    utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[DesRegistrationResponse]] =
    monitor("ConsumedAPI-DES-GetAgentRegistration-POST") {
      val url = s"$baseUrl/registration/individual/utr/${UriEncoding.encodePathSegment(utr.value, "UTF-8")}"
      httpClient.POST[DesRegistrationRequest, Option[JsValue]](url, DesRegistrationRequest(isAnAgent = false))(
        implicitly[Writes[DesRegistrationRequest]],
        implicitly[HttpReads[Option[JsValue]]],
        hc.copy(
          authorization = Some(Authorization(s"Bearer $authorizationToken")),
          extraHeaders = hc.extraHeaders :+ "Environment" -> environment),
        ec
      )
    }.map {
      case Some(r) =>
        Some(
          DesRegistrationResponse(
            (r \ "isAnIndividual").as[Boolean],
            (r \ "organisation" \ "organisationName").asOpt[String],
            (r \ "individual").asOpt[Individual]))
      case _ => None
    }
}
