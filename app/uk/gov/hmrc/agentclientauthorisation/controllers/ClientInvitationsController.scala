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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.agentclientauthorisation.connectors.{ AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class ClientInvitationsController @Inject() (invitationsService: InvitationsService,
                                  override val authConnector: AuthConnector) extends BaseController with AuthActions with HalWriter with ClientInvitationsHal  {

   def getDetailsForAuthenticatedClient() = onlyForClients.async { implicit request =>
    Future successful Ok(toHalResource(request.nino.value, request.path))
  }

  def getDetailsForClient(clientId: String) = onlyForClients.async { implicit request =>
    if (clientId == request.nino.value) {
      Future successful Ok(toHalResource(clientId, request.path))
    } else {
      Future successful NoPermissionOnClient
    }
  }

  def acceptInvitation(clientId: String, invitationId: String) = onlyForClients.async { implicit request =>
    actionInvitation(request.nino.value, invitationId, invitationsService.acceptInvitation)
  }

  def rejectInvitation(clientId: String, invitationId: String) = onlyForClients.async { implicit request =>
    actionInvitation(request.nino.value, invitationId, invitationsService.rejectInvitation)
  }

  private def actionInvitation(clientId: String, invitationId: String, action: Invitation => Future[Either[String, Invitation]]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if invitation.clientId == clientId => action(invitation) map {
        case Right(_) => NoContent
        case Left(message) => invalidInvitationStatus(message)
      }
      case None => Future successful InvitationNotFound
      case _ => Future successful NoPermissionOnClient
    }
  }

  def getInvitation(clientId: String, invitationId: String) = onlyForClients.async { implicit request =>
    invitationsService.findInvitation(invitationId).map {
      case Some(x) if x.clientId == request.nino.value => Ok(toHalResource(x))
      case None => InvitationNotFound
      case _ => NoPermissionOnClient
    }
  }

  def getInvitations(clientId: String, status: Option[InvitationStatus]) = onlyForClients.async { implicit request =>
    if (clientId == request.nino.value) {
      invitationsService.clientsReceived(SUPPORTED_SERVICE, clientId, status) map (results => Ok(toHalResource(results, clientId, status)))
    } else {
      Future successful NoPermissionOnClient
    }
  }

  override protected def agencyLink(invitation: Invitation): Option[String] = None
}
