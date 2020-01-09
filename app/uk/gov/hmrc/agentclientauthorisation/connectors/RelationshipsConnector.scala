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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject._
import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RelationshipsConnector @Inject()(appConfig: AppConfig, http: HttpClient, metrics: Metrics)
    extends HttpAPIMonitor {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val ISO_LOCAL_DATE_TIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS"

  private val baseUrl: String = appConfig.relationshipsBaseUrl
  private val afiBaseUrl: String = appConfig.afiRelationshipsBaseUrl

  def createMtdItRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-MTD-IT-PUT") {
      http.PUT[String, HttpResponse](mtdItRelationshipUrl(invitation).toString, "") map (_ => ())
    }

  def createMtdVatRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-MTD-VAT-PUT") {
      http.PUT[String, HttpResponse](mtdVatRelationshipUrl(invitation).toString, "") map (_ => ())
    }

  def createAfiRelationship(invitation: Invitation, acceptedDate: DateTime)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {
    val body = Json.obj("startDate" -> acceptedDate.toString(ISO_LOCAL_DATE_TIME_FORMAT))
    monitor(s"ConsumedAPI-AgentFiRelationship-relationships-${invitation.service.id}-PUT") {
      http.PUT[JsObject, HttpResponse](afiRelationshipUrl(invitation).toString, body) map (_ => ())
    }
  }

  def createTrustRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-Trust-PUT") {
      http.PUT[String, HttpResponse](trustRelationshipUrl(invitation).toString, "") map (_ => ())
    }

  def createCapitalGainsRelationship(
    invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-CapitalGains-PUT") {
      http.PUT[String, HttpResponse](cgtRelationshipUrl(invitation).toString, "") map (_ => ())
    }

  def getActiveRelationships(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Map[String, Seq[Arn]]] =
    monitor(s"ConsumedAPI-AgentClientRelationships-GetActive-GET") {
      val url = s"$baseUrl/agent-client-relationships/client/relationships/active"
      http.GET[Map[String, Seq[Arn]]](url)
    }

  def getActiveAfiRelationships(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[JsObject]] =
    monitor(s"ConsumedAPI-AgentFiRelationship-GetActive-GET") {
      val url = s"$afiBaseUrl/agent-fi-relationship/relationships/active"
      http.GET[Seq[JsObject]](url).recover {
        case _: NotFoundException => Seq()
      }
    }

  private def trustRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-TERS-ORG/client/SAUTR/${encodePathSegment(
      invitation.clientId.value)}"

  private def mtdItRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-MTD-IT/client/MTDITID/${encodePathSegment(
      invitation.clientId.value)}"

  private def mtdVatRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-MTD-VAT/client/VRN/${encodePathSegment(
      invitation.clientId.value)}"

  private def cgtRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-CGT-PD/client/CGTPDRef/${encodePathSegment(
      invitation.clientId.value)}"

  private def afiRelationshipUrl(invitation: Invitation): String = {
    val arn = encodePathSegment(invitation.arn.value)
    val service = encodePathSegment(invitation.service.id)
    val clientId = encodePathSegment(invitation.clientId.value)
    s"$afiBaseUrl/agent-fi-relationship/relationships/agent/$arn/service/$service/client/$clientId"
  }
}
