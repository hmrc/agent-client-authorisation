/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers

import javax.inject._

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class ClientInvitationsController @Inject()(invitationsService: InvitationsService)
                                           (implicit metrics: Metrics,
                                            microserviceAuthConnector: MicroserviceAuthConnector,
                                            auditService: AuditService)
  extends AuthConnector(metrics, microserviceAuthConnector) with HalWriter with ClientInvitationsHal {

  //  def getDetailsForAuthenticatedClient: Action[AnyContent] = onlyForClients.async {
  //    implicit request =>
  //      Future successful Ok(toHalResource(request.nino.value, request.path))
  //  }

  def getDetailsForClient(mtdItId: MtdItId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          Future successful Ok(toHalResource(mtdItId, request.path))
        }
  }

  def acceptInvitation(mtdItId: MtdItId, invitationId: String): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          actionInvitation(mtdItId, invitationId, invitation => invitationsService.acceptInvitation(invitation).andThen {
            case Success(Right(x)) => auditService.sendAgentClientRelationshipCreated(invitationId, x.arn, mtdItId)
          })
        }
  }

  def rejectInvitation(mtdItId: MtdItId, invitationId: String): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          actionInvitation(mtdItId, invitationId, invitationsService.rejectInvitation)
        }
  }

  private def actionInvitation(mtdItId: MtdItId, invitationId: String, action: Invitation => Future[Either[String, Invitation]])
                              (implicit hc: HeaderCarrier, request: Request[AnyContent]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation)
        if invitation.clientId == mtdItId.value =>
        action(invitation) map {
          case Right(invite) =>
            auditService.sendAgentInvitationResponse(invite.id.stringify, invite.arn, invite.status, mtdItId)
            NoContent
          case Left(message) => invalidInvitationStatus(message)
        }
      case None => Future successful InvitationNotFound
      case _ => Future successful NoPermissionOnClient
    }
  }

  def getInvitation(mtdItId: MtdItId, invitationId: String): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          invitationsService.findInvitation(invitationId).map {
            case Some(x) if x.clientId == mtdItId.value => Ok(toHalResource(x))
            case None => InvitationNotFound
            case _ => NoPermissionOnClient
          }
        }
  }

  def getInvitations(mtdItId: MtdItId, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          invitationsService.clientsReceived(SUPPORTED_SERVICE, mtdItId, status) map (results => Ok(toHalResource(results, mtdItId, status)))
        }
  }

  private def forThisClient(mtdItId: MtdItId)(block: => Future[Result])(implicit authMtdItId: MtdItId) =
    if (authMtdItId.value != mtdItId.value)
      Future successful NoPermissionOnClient
    else block

  override protected def agencyLink(invitation: Invitation): Option[String] = None
}
