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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import org.joda.time.DateTime.now
import play.api.hal.{Hal, HalLink, HalLinks, HalResource}
import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsObject, Json}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.controllers.HalWriter
import uk.gov.hmrc.agentclientauthorisation.controllers.SUPPORTED_REGIME
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class SandboxClientInvitationsController(override val authConnector: AuthConnector,
                                         override val agenciesFakeConnector: AgenciesFakeConnector
                                        ) extends BaseController with AuthActions with HalWriter {

  def acceptInvitation(clientId: String, invitationId: String) = onlyForSaClients { implicit request =>
    NoContent
  }

  def rejectInvitation(clientId: String, invitationId: String) = onlyForSaClients { implicit request =>
    NoContent
  }

  def getInvitation(clientId: String, invitationId: String) = onlyForSaClients { implicit request =>
    Ok(toHalResource(invitation(clientId), clientId))
  }

  def getInvitations(clientId: String, status: Option[InvitationStatus]) = onlyForSaClients { implicit request =>
    Ok(toHalResource(Seq(invitation(clientId), invitation(clientId)), clientId, status))
  }

  private def invitation(clientId: String) = Invitation(
        BSONObjectID.generate,
        Arn("agencyReference"),
        SUPPORTED_REGIME,
        clientId,
        "A11 1AA",
        List(StatusChangeEvent(now(), Pending))
      )


  private def toHalResource(invitation: Invitation, clientId: String): HalResource = {
    var links = HalLinks(Vector(HalLink("self", routes.SandboxClientInvitationsController.getInvitation(clientId, invitation.id.stringify).url)))

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", routes.SandboxClientInvitationsController.acceptInvitation(clientId, invitation.id.stringify).url)
      links = links ++ HalLink("reject", routes.SandboxClientInvitationsController.rejectInvitation(clientId, invitation.id.stringify).url)
    }

    HalResource(links, toJson(invitation).as[JsObject])
  }

  private def toHalResource(requests: Seq[Invitation], clientId: String, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource(_, clientId)).toVector

    val links = Vector(HalLink("self", routes.SandboxClientInvitationsController.getInvitations(clientId, status).url))
    Hal.hal(Json.obj(), links, Vector("invitations"-> requestResources))
  }
}
