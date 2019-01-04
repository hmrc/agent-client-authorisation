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
import org.joda.time.LocalDate
import org.joda.time.format._
import play.api.libs.json.{JsObject, JsPath, Reads}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http._

import scala.concurrent.{ExecutionContext, Future}

case class CitizenDateOfBirth(dateOfBirth: Option[LocalDate])

object CitizenDateOfBirth {
  val format = DateTimeFormat.forPattern("ddMMyyyy")
  implicit val reads: Reads[CitizenDateOfBirth] =
    (JsPath \ "dateOfBirth")
      .readNullable[String]
      .map {
        case Some(dob) => CitizenDateOfBirth(Some(LocalDate.parse(dob, format)))
        case None      => CitizenDateOfBirth(None)
      }
}

@Singleton
class CitizenDetailsConnector @Inject()(
  @Named("citizen-details-baseUrl") baseUrl: URL,
  http: HttpGet with HttpDelete,
  metrics: Metrics)
    extends HttpAPIMonitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getCitizenDateOfBirth(
    nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[CitizenDateOfBirth]] =
    monitor(s"ConsumedAPI-CitizenDetails-GET") {
      val url = new URL(baseUrl, s"/citizen-details/nino/${nino.value}")
      http.GET[Option[CitizenDateOfBirth]](url.toString).recover {
        case _ => None
      }
    }
}
