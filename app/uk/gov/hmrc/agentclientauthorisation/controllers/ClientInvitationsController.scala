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

import play.api.hal.{Hal, HalLink, HalLinks, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Pending}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class ClientInvitationsController(invitationsService: InvitationsService,
                                  override val authConnector: AuthConnector,
                                  override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions with HalWriter{

  private val SUPPORTED_REGIME = "mtd-sa"

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
      case Some(x) if x.clientId == request.mtdClientId.value => Ok(toHalResource(x, clientId))
      case None => NotFound
      case _ => Forbidden
    }
  }

  def getInvitations(clientId: String) = onlyForSaClients.async { implicit request =>
    if (clientId == request.mtdClientId.value) {
      invitationsService.list(SUPPORTED_REGIME, clientId) map (results => Ok(toHalResource(results, clientId)))
    } else {
      Future successful Forbidden
    }
  }

  private def toHalResource(invitation: Invitation, clientId: String): HalResource = {
    var links = HalLinks(Vector(HalLink("self", routes.ClientInvitationsController.getInvitation(clientId, invitation.id.stringify).url)))
    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", routes.ClientInvitationsController.acceptInvitation(clientId, invitation.id.stringify).url)
      links = links ++ HalLink("reject", routes.ClientInvitationsController.rejectInvitation(clientId, invitation.id.stringify).url)
    }
    HalResource(links, toJson(invitation).as[JsObject])
  }

  private def toHalResource(requests: List[Invitation], clientId: String): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource(_, clientId)).toVector

    val links = Vector(HalLink("self", routes.ClientInvitationsController.getInvitations(clientId).url))
    Hal.hal(Json.obj(), links, Vector("invitations"-> requestResources))
  }
}
