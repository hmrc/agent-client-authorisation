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

import com.codahale.metrics.MetricRegistry
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import play.api.Logger
import play.api.libs.json.{JsObject, JsPath, Reads}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.model.{AgencyEmail, AgencyEmailNotFound, CustomerDetails}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpGet, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

case class AgencyName(name: Option[String])

case class AgencyNameNotFound() extends Exception

object AgencyName {
  implicit val nameReads: Reads[AgencyName] =
    (JsPath \ "agencyName").readNullable[String].map(AgencyName(_))
}

@ImplementedBy(classOf[AgentServicesAccountConnectorImpl])
trait AgentServicesAccountConnector {
  def getAgencyNameAgent(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]
  def getAgencyNameViaClient(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]
  def getAgencyEmailBy(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String]
  def getTradingName(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]]
  def getCustomerDetails(vrn: Vrn)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[CustomerDetails]
  def getNinoForMtdItId(mtdItId: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]]
}

@Singleton
class AgentServicesAccountConnectorImpl @Inject()(
  @Named("agent-services-account-baseUrl") baseUrl: URL,
  http: HttpClient,
  metrics: Metrics)
    extends HttpAPIMonitor with AgentServicesAccountConnector {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getAgencyNameAgent(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor(s"ConsumedAPI-Get-AgencyName-GET") {
      http.GET[AgencyName](new URL(baseUrl, s"/agent-services-account/agent/agency-name").toString).map(_.name)
    } recoverWith {
      case _: NotFoundException => Future failed AgencyNameNotFound()
    }

  def getAgencyNameViaClient(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor("ConsumedAPI-Get-AgencyNameByArn-GET") {
      http
        .GET[AgencyName](new URL(baseUrl, s"/agent-services-account/client/agency-name/${arn.value}").toString)
        .map(_.name)
    } recoverWith {
      case _: NotFoundException => Future.failed(AgencyNameNotFound())
    }

  def getAgencyEmailBy(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[String] =
    monitor("ConsumedAPI-Get-AgencyEmailByArn-GET") {
      http
        .GET[AgencyEmail](new URL(baseUrl, s"/agent-services-account/client/agency-email/${arn.value}").toString)
        .map(_.email)
    } recoverWith {
      case _: NotFoundException => {
        Future.failed(AgencyEmailNotFound())
      }
    }

  def getTradingName(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor(s"ConsumedAPI-Get-TradingName-POST") {
      http
        .GET[JsObject](new URL(baseUrl, s"/agent-services-account/client/trading-name/nino/${nino.value}").toString)
        .map(obj => (obj \ "tradingName").asOpt[String])
    }.recover {
      case _: NotFoundException => None
    }

  def getCustomerDetails(vrn: Vrn)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[CustomerDetails] =
    monitor(s"ConsumedAPI-Get-VatOrgName-POST") {
      http
        .GET[CustomerDetails](
          new URL(baseUrl, s"/agent-services-account/client/vat-customer-details/vrn/${vrn.value}").toString)
    }.recover {
      case _: NotFoundException => CustomerDetails(None, None, None)
    }

  def getNinoForMtdItId(mtdItId: MtdItId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Nino]] =
    monitor(s"ConsumedAPI-Get-NinoForMtdItId-GET") {
      http
        .GET[JsObject](new URL(baseUrl, s"/agent-services-account/client/mtdItId/${mtdItId.value}").toString)
        .map(obj => (obj \ "nino").asOpt[Nino])
    }.recover {
      case e => {
        Logger(getClass).error(s"Unable to translate MtdItId: ${e.getMessage}")
        None
      }
    }
}
