package uk.gov.hmrc.agentclientauthorisation.controllers
import com.google.inject.Provider
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.http.MediaType.parse
import play.api.mvc.Action
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, EmailConnector}
import uk.gov.hmrc.auth.core.AuthConnector
import play.api.mvc.{Action, AnyContent, Result}

import scala.concurrent.{ExecutionContextExecutor, Future}

@Singleton
class EmailStatusInvitationController @Inject()(emailConnector: EmailConnector)(implicit metrics: Metrics,
                                                                                authConnector: AuthConnector,
                                                                                auditService: AuditService,
                                                                                ecp: Provider[ExecutionContextExecutor]) extends AuthActions(metrics, authConnector) {

  val sendEmail = Action.async { implicit request =>

//    emailConnector.sendEmail()
    Future successful Accepted
  }

}
