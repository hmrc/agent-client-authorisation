package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Provider}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, InvitationStatus, NinoType, Service}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class NiClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
  extends BaseClientInvitationsController(invitationsService, metrics, authConnector, auditService) {

  implicit val ec: ExecutionContext = ecp.get

  def acceptInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      acceptInvitation(ClientIdentifier(nino), invitationId)
  }

  def rejectInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      forThisClient(ClientIdentifier(nino)) {
        actionInvitation(ClientIdentifier(nino), invitationId, invitationsService.rejectInvitation)
      }
  }

  def getInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      getInvitation(ClientIdentifier(nino), invitationId)
  }

  def getInvitations(nino: Nino, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      getInvitations(Service.PersonalIncomeRecord, ClientIdentifier(nino), status)
  }

  def onlyForClients(action: Request[AnyContent] => ClientIdentifier[Nino] => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.PersonalIncomeRecord, NinoType)(action)

}
