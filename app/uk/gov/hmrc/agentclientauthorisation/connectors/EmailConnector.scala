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
import javax.inject.{Inject, Named}
import play.api.Logger
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost, HttpResponse}
import play.api.mvc.Results.Accepted

import scala.concurrent.ExecutionContext

class EmailConnector @Inject()(@Named("email-baseUrl") baseUrl: URL, http: HttpPost, metrics: Metrics)
    extends HttpAPIMonitor {
  val url = new URL(s"$baseUrl/hmrc/email")

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    monitor(s"Send-Email-${emailInformation.templateId}") {
      Logger.error(s"the email info sent is: $emailInformation")
      http.POST[EmailInformation, HttpResponse](url.toString, emailInformation).map(_ => Accepted("email sent"))
    }.recover {
      case e => throw new Exception(s"sending email failed: $e")
    }
}
