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
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named, Singleton}
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.{Eori, Utr}
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

case class GetEoriPayload(postcode: String)

object GetEoriPayload {
  implicit val format: Format[GetEoriPayload] = Json.format[GetEoriPayload]
}

case class GetEoriResponse(niBusiness: NiBusiness)

object GetEoriResponse {
  implicit val niBusinessSubscriptionFormat: Format[NiBusinessSubscription] = Json.format[NiBusinessSubscription]

  implicit val niBusinessFormat: Format[NiBusiness] = Json.format[NiBusiness]

  implicit val GetEoriResponseFormat: Format[GetEoriResponse] = Json.format[GetEoriResponse]
}

case class NiBusiness(name: String, subscription: NiBusinessSubscription)

case class NiBusinessSubscription(status: String, eori: Option[Eori])

@Singleton
class NiExemptionRegistrationConnector @Inject()(
  @Named("ni-exemption-registration-baseUrl") baseUrl: URL,
  http: HttpPost,
  metrics: Metrics)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getEori(utr: Utr, postcode: String)(
    implicit c: HeaderCarrier,
    ec: ExecutionContext): Future[Option[GetEoriResponse]] =
    monitor(s"ConsumedAPI-NI-Exemption-Registration-POST") {

      val url = new URL(baseUrl, s"/ni-exemption-registration/ni-businesses/${utr.value}").toString

      http.POST[GetEoriPayload, Option[GetEoriResponse]](url, GetEoriPayload(postcode)).recover {
        case Upstream4xxResponse(_, 409, _, _) => None
      }
    }
}
