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
import play.api.libs.json.{Format, JsObject, Json, OFormat}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class SimpleCbcSubscription(tradingName: String, isGBUser: Boolean, emails: Option[Seq[String]])
object SimpleCbcSubscription {
  implicit val format: Format[SimpleCbcSubscription] = Json.format[SimpleCbcSubscription]
}

/*
 Note: Most of the code in this file was copy-pasted from the country-by-country-reporting service repository.
 */

private case class DisplaySubscriptionForCbCRequest(
  displaySubscriptionForCbCRequest: DisplaySubscriptionDetails
)
private object DisplaySubscriptionForCbCRequest {
  implicit val format: OFormat[DisplaySubscriptionForCbCRequest] =
    Json.format[DisplaySubscriptionForCbCRequest]
}

//-----------------------------------------------------------------------------

private case class DisplaySubscriptionDetails(
  requestCommon: RequestCommonForSubscription,
  requestDetail: ReadSubscriptionRequestDetail
)
private object DisplaySubscriptionDetails {
  implicit val format: OFormat[DisplaySubscriptionDetails] =
    Json.format[DisplaySubscriptionDetails]
}

//-----------------------------------------------------------------------------

private case class RequestCommonForSubscription(
  regime: String,
  receiptDate: String,
  acknowledgementReference: String,
  originatingSystem: String,
  conversationID: Option[String]
)
private object RequestCommonForSubscription {
  //Format: ISO 8601 YYYY-MM-DDTHH:mm:ssZ e.g. 2020-09-23T16:12:11Zs
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")

  implicit val requestCommonForSubscriptionFormats: OFormat[RequestCommonForSubscription] =
    Json.format[RequestCommonForSubscription]

  def apply(): RequestCommonForSubscription = {
    //Generate a 32 chars UUID without hyphens
    val acknowledgementReference = UUID.randomUUID().toString.replace("-", "")

    RequestCommonForSubscription(
      regime = "CBC",
      receiptDate = ZonedDateTime.now().format(formatter),
      acknowledgementReference = acknowledgementReference,
      originatingSystem = "MDTP",
      conversationID = None
    )
  }
}

//-----------------------------------------------------------------------------

private case class ReadSubscriptionRequestDetail(IDType: String, IDNumber: String)
private object ReadSubscriptionRequestDetail {
  implicit val format: OFormat[ReadSubscriptionRequestDetail] =
    Json.format[ReadSubscriptionRequestDetail]
  def apply(subscriptionId: String): ReadSubscriptionRequestDetail = new ReadSubscriptionRequestDetail("CBC", subscriptionId)
}

//=============================================================================
@ImplementedBy(classOf[EisConnectorImpl])
trait EisConnector {
  def getCbcSubscription(cbcId: CbcId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[SimpleCbcSubscription]]
}

@Singleton
class EisConnectorImpl @Inject()(config: AppConfig, http: HttpClient, metrics: Metrics)
    extends HttpAPIMonitor with EisConnector with HttpErrorFunctions with Logging {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getCbcSubscriptionJson(cbcId: CbcId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[JsObject]] = {
    val conversationId = hc.sessionId.map(_.value).getOrElse(UUID.randomUUID().toString)
    val request = DisplaySubscriptionForCbCRequest(
      displaySubscriptionForCbCRequest = DisplaySubscriptionDetails(
        requestCommon = RequestCommonForSubscription().copy(conversationID = Some(conversationId)),
        requestDetail = ReadSubscriptionRequestDetail(IDType = "CbC", IDNumber = cbcId.value)
      )
    )

    val extraHeaders = Seq(
      HeaderNames.authorisation -> s"Bearer ${config.eisAuthToken}",
      "x-forwarded-host"        -> "mdtp",
      "x-correlation-id"        -> UUID.randomUUID().toString,
      "x-conversation-id"       -> conversationId,
      "date"                    -> ZonedDateTime.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss O")),
      "content-type"            -> "application/json",
      "accept"                  -> "application/json",
      "Environment"             -> config.eisEnvironment
    )

    monitor(s"ConsumedAPI-EIS-dct50d-POST") {
      http
        .POST[DisplaySubscriptionForCbCRequest, HttpResponse](
          config.eisBaseUrl + "/dac/dct50d/v1",
          request,
          headers = extraHeaders
        )
        .map { response =>
          response.status match {
            case OK        => Some(response.json.as[JsObject])
            case NOT_FOUND => None
            case status    => throw UpstreamErrorResponse(s"Error when retrieving CBC subscription: status $status", status)
          }
        }
    }
  }

  def getCbcSubscription(cbcId: CbcId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[SimpleCbcSubscription]] =
    getCbcSubscriptionJson(cbcId).map { maybeRecord =>
      maybeRecord.map { record =>
        val mTradingName = (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "tradingName").asOpt[String]
        val mIsGBUser = (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "isGBUser").asOpt[Boolean]
        val primaryContacts =
          (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "primaryContact").asOpt[Seq[JsObject]].getOrElse(Seq.empty)
        val secondaryContacts =
          (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "secondaryContact").asOpt[Seq[JsObject]].getOrElse(Seq.empty)
        val emails = (primaryContacts ++ secondaryContacts).flatMap(jsObj => (jsObj \ "email").asOpt[String])

        (mTradingName, mIsGBUser) match {
          case (Some(tradingName), Some(isGBUser)) => SimpleCbcSubscription(tradingName, isGBUser, Some(emails))
          case _                                   => throw new RuntimeException("CBC subscription response did not contain complete information")
        }
      }
    }
}
