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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class ClientInvitationsController(invitationsService: InvitationsService,
                                  override val authConnector: AuthConnector,
                                  override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions {

  def acceptInvitation(clientRegimeId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    actionInvitation(request.mtdClientId.value, invitationId, invitationsService.acceptInvitation)
  }

  def rejectInvitation(clientRegimeId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    actionInvitation(request.mtdClientId.value, invitationId, invitationsService.rejectInvitation)
  }

  private def actionInvitation(clientRegimeId: String, invitationId: String, action: Invitation => Future[Boolean]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if invitation.clientRegimeId == clientRegimeId => action(invitation) map {
        case true => NoContent
        case false => Forbidden
      }
      case None => Future successful NotFound
      case _ => Future successful Forbidden
    }
  }
}
