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

import akka.Done
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.{AnalyticsRequest, DimensionValue, Event}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PlatformAnalyticsConnectorImpl])
trait PlatformAnalyticsConnector {
  def sendEvent(request: AnalyticsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done]
}

@Singleton
class PlatformAnalyticsConnectorImpl @Inject()(appConfig: AppConfig, http: HttpClient)
    extends PlatformAnalyticsConnector {

  val serviceUrl: String = appConfig.platformAnalyticsBaseUrl

  private implicit val dimensionWrites = Json.writes[DimensionValue]
  private implicit val eventWrites = Json.writes[Event]
  private implicit val analyticsWrites = Json.writes[AnalyticsRequest]

  def sendEvent(request: AnalyticsRequest)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    val url = s"$serviceUrl/platform-analytics/event"
    http.POST[AnalyticsRequest, HttpResponse](url, request).map(_ => Done).recover {
      case _: Throwable =>
        Logger(getClass).error(s"Couldn't send analytics event $request")
        Done
    }
  }
}
