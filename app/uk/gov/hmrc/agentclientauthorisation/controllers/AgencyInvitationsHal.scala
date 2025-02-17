/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.Json._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.agentclientauthorisation.controllers.routes

trait AgencyInvitationsHal {

  def toHalResource(
    invitations: List[Invitation],
    arn: Arn,
    clientType: Option[String],
    service: Option[String],
    clientIdType: Option[String],
    clientId: Option[String],
    status: Option[InvitationStatus]
  ): HalResource = {
    val invitationResources = invitations.map(toHalResource).toVector
    val selfLink = Vector(
      HalLink(
        "self",
        routes.AgencyInvitationsController
          .getSentInvitations(arn, clientType, service, clientIdType, clientId, status, None)
          .url
      )
    )
    Hal.hal(Json.obj(), selfLink ++ invitationLinks(invitations), Vector("invitations" -> invitationResources))
  }

  private def invitationLinks(invitations: List[Invitation]): Vector[HalLink] =
    invitations.map { i =>
      HalLink("invitations", routes.AgencyInvitationsController.getSentInvitation(i.arn, i.invitationId).toString)
    }.toVector

  def toHalResource(invitation: Invitation): HalResource = {
    val links = HalLinks(Vector(HalLink("self", routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.invitationId).url)))

    HalResource(links, toJson(invitation)(Invitation.external.writes).as[JsObject])
  }

}
