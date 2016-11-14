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
import play.api.mvc.{Action, Result}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{AgentRequest, AuthActions}
import uk.gov.hmrc.agentclientauthorisation.controllers.HalWriter
import uk.gov.hmrc.agentclientauthorisation.controllers.SUPPORTED_REGIME
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class SandboxAgencyInvitationsController(override val authConnector: AuthConnector,
                                         override val agenciesFakeConnector: AgenciesFakeConnector
                                        ) extends BaseController with AuthActions with HalWriter {

  def createInvitation(arn: Arn) = onlyForSaAgents { implicit request =>
      Created.withHeaders(location(arn, "invitationId"))
  }

  private def location(arn: Arn, invitationId: String) = {
    LOCATION -> routes.SandboxAgencyInvitationsController.getSentInvitation(arn, invitationId).url
  }

  def getSentInvitations(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]) = onlyForSaAgents { implicit request =>
    Ok(toHalResource(List(invitation(arn), invitation(arn)), arn))
  }

  def getSentInvitation(arn: Arn, invitationId: String) = onlyForSaAgents { implicit request =>
    Ok(toHalResource(invitation(arn), arn))
  }

  def cancelInvitation(arn: Arn, invitation: String) = onlyForSaAgents { implicit request =>
    NoContent
  }

  private def invitation(arn: Arn) = Invitation(
        BSONObjectID.generate,
        arn,
        SUPPORTED_REGIME,
        "clientId",
        "A11 1AA",
        List(StatusChangeEvent(now(), Pending))
      )

  private def toHalResource(requests: List[Invitation], arn: Arn): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource(_, arn)).toVector

    val links = Vector(HalLink("self", routes.SandboxAgencyInvitationsController.getSentInvitations(arn, None, None, None).url))
    Hal.hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

  private def toHalResource(invitation: Invitation, arn: Arn): HalResource = {
    var links = HalLinks(Vector(HalLink("self", routes.SandboxAgencyInvitationsController.getSentInvitation(arn, invitation.id.stringify).url)))
    if (invitation.status == Pending) {
      links = links ++ HalLink("cancel", routes.SandboxAgencyInvitationsController.cancelInvitation(arn, invitation.id.stringify).url)
    }
    HalResource(links, toJson(invitation).as[JsObject])
  }
}

