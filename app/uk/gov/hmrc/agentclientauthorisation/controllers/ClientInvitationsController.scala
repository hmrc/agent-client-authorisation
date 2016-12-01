/*
 * Copyright 2016 HM Revenue & Customs
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
import play.api.mvc.Call
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, Invitation, InvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

@Singleton
class ClientInvitationsController @Inject() (invitationsService: InvitationsService,
                                  override val authConnector: AuthConnector,
                                  override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions with HalWriter with ClientInvitationsHal  {

   def getDetailsForAuthenticatedClient() = onlyForSaClients.async { implicit request =>
    Future successful Ok(toHalResource(request.mtdClientId.value, request.path))
  }

  def getDetailsForClient(clientId: String) = onlyForSaClients.async { implicit request =>
    if (clientId == request.mtdClientId.value) {
      Future successful Ok(toHalResource(clientId, request.path))
    } else {
      Future successful Forbidden
    }
  }

  def acceptInvitation(clientId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    actionInvitation(request.mtdClientId.value, invitationId, invitationsService.acceptInvitation)
  }

  def rejectInvitation(clientId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    actionInvitation(request.mtdClientId.value, invitationId, invitationsService.rejectInvitation)
  }

  private def actionInvitation(clientId: String, invitationId: String, action: Invitation => Future[Boolean]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if invitation.clientId == clientId => action(invitation) map {
        case true => NoContent
        case false => Forbidden
      }
      case None => Future successful NotFound
      case _ => Future successful Forbidden
    }
  }
  def getInvitation(clientId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    invitationsService.findInvitation(invitationId).map {
      case Some(x) if x.clientId == request.mtdClientId.value => Ok(toHalResource(x))
      case None => NotFound
      case _ => Forbidden
    }
  }

  def getInvitations(clientId: String, status: Option[InvitationStatus]) = onlyForSaClients.async { implicit request =>
    if (clientId == request.mtdClientId.value) {
      invitationsService.clientsReceived(SUPPORTED_REGIME, clientId, status) map (results => Ok(toHalResource(results, clientId, status)))
    } else {
      Future successful Forbidden
    }
  }

  override protected val reverseRoutes: ReverseClientInvitationsRoutes = ReverseClientInvitations
  override protected def agencyLink(invitation: Invitation): Option[String] =
    Some(agenciesFakeConnector.agencyUrl(invitation.arn).toString)
}

private object ReverseClientInvitations extends ReverseClientInvitationsRoutes {
  override def getInvitation(clientId:String, invitationId:String): Call = routes.ClientInvitationsController.getInvitation(clientId, invitationId)
  override def getInvitations(clientId:String, status:Option[InvitationStatus]): Call = routes.ClientInvitationsController.getInvitations(clientId, status)
  override def acceptInvitation(clientId:String, invitationId:String): Call = routes.ClientInvitationsController.acceptInvitation(clientId, invitationId)
  override def rejectInvitation(clientId:String, invitationId:String): Call = routes.ClientInvitationsController.rejectInvitation(clientId, invitationId)
}
