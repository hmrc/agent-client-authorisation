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

case class SimpleCbcSubscription(tradingName: Option[String], otherNames: Seq[String], isGBUser: Boolean, emails: Seq[String]) {
  def anyAvailableName: Option[String] = tradingName.orElse(otherNames.headOption)
}
object SimpleCbcSubscription {
  implicit val format: Format[SimpleCbcSubscription] = Json.format[SimpleCbcSubscription]
  def fromDisplaySubscriptionForCbCResponse(record: JsObject): SimpleCbcSubscription = {
    val mIsGBUser = (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "isGBUser").asOpt[Boolean]
    val mTradingName = (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "tradingName").asOpt[String]
    val primaryContact: CbcContact =
      (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "primaryContact")
        .asOpt[CbcContact]
        .getOrElse(
          throw new RuntimeException("CBC subscription response did not contain complete information")
        )
    val secondaryContact: CbcContact =
      (record \ "displaySubscriptionForCbCResponse" \ "responseDetail" \ "secondaryContact")
        .asOpt[CbcContact]
        .getOrElse(
          throw new RuntimeException("CBC subscription response did not contain complete information")
        )
    val contacts = Seq(primaryContact) ++ Seq(secondaryContact)

    val otherNames: Seq[String] = contacts.collect {
      case CbcContact(_, Some(ind: CbcIndividual), _)   => ind.name
      case CbcContact(_, _, Some(org: CbcOrganisation)) => org.organisationName
    }

    val emails = contacts.map(_.email).collect { case eml => eml }

    mIsGBUser match {
      case Some(isGBUser) => SimpleCbcSubscription(mTradingName, otherNames, isGBUser, emails)
      case _              => throw new RuntimeException("CBC subscription response did not contain complete information")
    }
  }
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
      regime = "CbC",
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

//-----------------------------------------------------------------------------

private case class CbcIndividual(firstName: String, lastName: String) { def name: String = s"$firstName $lastName" }
private object CbcIndividual { implicit val format: Format[CbcIndividual] = Json.format[CbcIndividual] }
private case class CbcOrganisation(organisationName: String)
private object CbcOrganisation { implicit val format: Format[CbcOrganisation] = Json.format[CbcOrganisation] }
private case class CbcContact(email: String, individual: Option[CbcIndividual], organisation: Option[CbcOrganisation])
private object CbcContact { implicit val format: Format[CbcContact] = Json.format[CbcContact] }

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
          config.eisBaseUrl + "/dac6/dct50d/v1",
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
      maybeRecord.map(SimpleCbcSubscription.fromDisplaySubscriptionForCbCResponse)
    }
}
