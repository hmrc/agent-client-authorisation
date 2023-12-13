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

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@ImplementedBy(classOf[DesConnectorImpl])
trait DesConnector {

  def getVatDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatDetails]]

  def getCgtSubscription(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[CgtSubscriptionResponse]

  def getAgencyDetails(agentIdentifier: Either[Utr, Arn])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]]

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]

  def getVatCustomerDetails(vrn: Vrn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[VatCustomerDetails]]

  def getPillar2Details(plrId: PlrId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Pillar2DetailsResponse]

  def getPillar2Subscription(plrId: PlrId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Pillar2SubscriptionResponse]
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
    val url = s"$baseUrl/vat/customer/vrn/${encodePathSegment(vrn.value)}/information"
    getWithDesIfHeaders("GetVatCustomerInformation", url).map { response =>
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

  def getPillar2RecordSubscription(plrId: PlrId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Pillar2Error, Pillar2Record]] = {
    val url = s"$baseUrl/pillar2/subscription?plrReference=${plrId.value}"
    agentCacheProvider.pillar2SubscriptionCache(plrId.value) {
      getWithDesIfHeaders(apiName = "getPillar2Subscription", url = url, viaIf = true).map { response =>
        response.status match {
          case 200 =>
            response.json.asOpt[Pillar2Record].toRight(Pillar2Error(NOT_FOUND, Seq.empty[DesError]))
          case _ =>
            Try((response.json \ "failures").asOpt[Seq[DesError]] match {
              case Some(e) => Left(Pillar2Error(response.status, e))
              case None    => Left(Pillar2Error(response.status, Seq(response.json.as[DesError])))
            }).toOption
              .getOrElse(Left(Pillar2Error(response.status, Seq.empty[DesError])))
        }
      }
    }
  }

  def getPillar2Subscription(plrId: PlrId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Pillar2SubscriptionResponse] =
    getPillar2RecordSubscription(plrId)
      .map(_.map(Pillar2Subscription.fromPillar2Record))
      .map(Pillar2SubscriptionResponse)

  def getPillar2Details(plrId: PlrId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Pillar2DetailsResponse] =
    getPillar2RecordSubscription(plrId)
      .map(_.left.map {
        case e @ Pillar2Error(NOT_FOUND, _) => e
        case e =>
          throw UpstreamErrorResponse("e.errors.toString()", e.httpResponseCode)
      })
      .map(_.map(Pillar2Details.fromPillar2Record))
      .map(Pillar2DetailsResponse)

  def getAgencyDetails(
    agentIdentifier: Either[Utr, Arn])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[AgentDetailsDesResponse]] = {
    val agentIdValue = agentIdentifier.fold(_.value, _.value)
    agentCacheProvider
      .agencyDetailsCache(agentIdValue) {
        getWithDesIfHeaders("getAgencyDetails", getAgentRecordUrl(agentIdentifier)).map { response =>
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

  def getBusinessName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor("ConsumedAPI-DES-GetAgentRegistration-POST") {
      val url = s"$baseUrl/registration/individual/utr/${UriEncoding.encodePathSegment(utr.value, "UTF-8")}"

      httpClient
        .POST[DesRegistrationRequest, HttpResponse](
          url,
          body = DesRegistrationRequest(isAnAgent = false),
          headers = desIfHeaders.outboundHeaders(viaIF = false))(
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
      httpClient.GET[HttpResponse](url, headers = desIfHeaders.outboundHeaders(viaIf, Some(apiName)))(implicitly[HttpReads[HttpResponse]], hc, ec)
    }

  private def getAgentRecordUrl(agentId: Either[Utr, Arn]) =
    agentId match {
      case Right(Arn(arn)) =>
        val encodedArn = UriEncoding.encodePathSegment(arn, "UTF-8")
        s"$baseUrl/registration/personal-details/arn/$encodedArn"
      case Left(Utr(utr)) =>
        val encodedUtr = UriEncoding.encodePathSegment(utr, "UTF-8")
        s"$baseUrl/registration/personal-details/utr/$encodedUtr"
    }

}
