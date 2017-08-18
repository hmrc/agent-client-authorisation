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

import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientInvitationsController @Inject()(invitationsService: InvitationsService,
                                            override val authConnector: AuthConnector) extends BaseController
  with AuthActions with HalWriter with ClientInvitationsHal {

//  def getDetailsForAuthenticatedClient: Action[AnyContent] = onlyForClients.async {
//    implicit request =>
//      Future successful Ok(toHalResource(request.nino.value, request.path))
//  }

  def getDetailsForClient(nino: Nino): Action[AnyContent] = Action.async {
    implicit request =>
      Future successful {
        Ok(toHalResource(nino, request.path))
//        if (clientId == request.nino.value) Ok(toHalResource(clientId, request.path))
//        else NoPermissionOnClient
      }
  }

  def acceptInvitation(nino: Nino, invitationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      actionInvitation(nino, invitationId, invitationsService.acceptInvitation)
  }

  def rejectInvitation(nino: Nino, invitationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      actionInvitation(nino, invitationId, invitationsService.rejectInvitation)
  }

  private def actionInvitation(nino: Nino, invitationId: String, action: Invitation => Future[Either[String, Invitation]])
                              (implicit ec: ExecutionContext) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation)
        if invitation.suppliedClientId == nino.value =>
          action(invitation) map {
            case Right(_) => NoContent
            case Left(message) => invalidInvitationStatus(message)
      }
      case None => Future successful InvitationNotFound
      case _ => Future successful NoPermissionOnClient
    }
  }

  def getInvitation(nino: Nino, invitationId: String): Action[AnyContent] = Action.async {
    implicit request =>
      invitationsService.findInvitation(invitationId).map {
        case Some(x) if x.clientId == nino.value => Ok(toHalResource(x))
        case None => InvitationNotFound
        case _ => NoPermissionOnClient
      }
  }

  def getInvitations(nino: Nino, status: Option[InvitationStatus]): Action[AnyContent] = Action.async {
    implicit request =>
      //      if (clientId == request.nino.value) {
      invitationsService.translateToMtdItId(nino.value,"ni") flatMap {
        case Some(mtdItId) =>
          invitationsService.clientsReceived(SUPPORTED_SERVICE, mtdItId, status) map (
            results => Ok(toHalResource(results, nino, status)))
        case None => Future successful InvitationNotFound
      }
      //else Future successful NoPermissionOnClient
  }

  override protected def agencyLink(invitation: Invitation): Option[String] = None
}
