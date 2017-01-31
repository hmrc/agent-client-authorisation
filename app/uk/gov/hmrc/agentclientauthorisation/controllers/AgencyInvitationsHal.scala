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

import play.api.hal.{Hal, HalLink, HalLinks, HalResource}
import play.api.libs.json.Json._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, Invitation, InvitationStatus, Pending}

trait AgencyInvitationsHal {

  protected def agencyLink(invitation: Invitation): Option[String]

  def toHalResource(arn: Arn, selfLinkHref: String): HalResource = {
    val selfLink = Vector(HalLink("self", selfLinkHref))
    val invitationsSentLink = Vector(HalLink("sent", routes.AgencyInvitationsController.getSentInvitations(arn, None, None, None).url))
    Hal.hal(Json.obj(), selfLink ++ invitationsSentLink, Vector())
  }

  def toHalResource(invitations: List[Invitation], arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]): HalResource = {
    val invitationResources = invitations.map(toHalResource).toVector

    val selfLink = Vector(HalLink("self", routes.AgencyInvitationsController.getSentInvitations(arn, regime, clientId, status).url))
    Hal.hal(Json.obj(), selfLink ++ invitationLinks(invitations), Vector("invitations" -> invitationResources))
  }

  private def invitationLinks(invitations: List[Invitation]): Vector[HalLink] = {
    invitations.map { i => HalLink("invitation", routes.AgencyInvitationsController.getSentInvitation(i.arn, i.id.stringify).toString)}.toVector
  }

  def toHalResource(invitation: Invitation): HalResource = {
    var links = HalLinks(Vector(HalLink("self", routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.id.stringify).url)))

    agencyLink(invitation).foreach(href => links = links ++ HalLink("agency", href))

    if (invitation.status == Pending) {
      links = links ++ HalLink("cancel", routes.AgencyInvitationsController.cancelInvitation(invitation.arn, invitation.id.stringify).url)
    }
    HalResource(links, toJson(invitation).as[JsObject])
  }

}
