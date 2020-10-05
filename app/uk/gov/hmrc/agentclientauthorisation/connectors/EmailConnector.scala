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
import com.google.inject.ImplementedBy
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import play.api.Logging
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpErrorFunctions, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EmailConnectorImpl])
trait EmailConnector {
  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit]
}

class EmailConnectorImpl @Inject()(appConfig: AppConfig, http: HttpClient, metrics: Metrics)
    extends HttpAPIMonitor with EmailConnector with HttpErrorFunctions with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val baseUrl: String = appConfig.emailBaseUrl

  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"Send-Email-${emailInformation.templateId}") {
      http
        .POST[EmailInformation, HttpResponse](s"$baseUrl/hmrc/email", emailInformation)
        .map { response =>
          response.status match {
            case status if is2xx(status) => ()
            case other =>
              logger.warn(s"unexpected status from email service, status: $other")
              ()
          }
        }
    }
}
