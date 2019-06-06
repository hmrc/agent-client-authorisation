package uk.gov.hmrc.agentclientauthorisation.connectors
import java.net.URL

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.http.{HeaderCarrier, HttpPost, HttpResponse}

import scala.concurrent.ExecutionContext

class EmailConnector @Inject()(@Named("email-baseUrl") baseUrl: URL, http: HttpPost, metrics: Metrics) extends HttpAPIMonitor {
  val url = new URL(s"$baseUrl/hmrc/email")

  override  val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def sendEmail(emailInformation: EmailInformation)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    monitor(s"Send-Email-${emailInformation.templateId}") {
      http.POST[EmailInformation, HttpResponse](url.toString, emailInformation)
    }
  }
}
