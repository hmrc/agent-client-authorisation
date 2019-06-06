package uk.gov.hmrc.agentclientauthorisation.controllers
import com.google.inject.Provider
import com.kenshoo.play.metrics.Metrics
import javax.inject.Inject
import play.api.http.MediaType.parse
import play.api.mvc.Action
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.EmailConnector
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.ExecutionContextExecutor

@Singleton
class EmailStatusInvitationController @Inject()(emailConnector: EmailConnector)(implicit metrics: Metrics,
                                                                                authConnector: AuthConnector,
                                                                                auditService: AuditService,
                                                                                ecp: Provider[ExecutionContextExecutor]) {

  def sendEmail() = Action.async { implicit request =>

    emailConnector.sendEmail()
  }

}
