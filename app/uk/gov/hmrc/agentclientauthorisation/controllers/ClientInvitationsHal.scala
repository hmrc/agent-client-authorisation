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

import play.api.hal.Hal.hal
import play.api.hal.{HalLink, HalLinks, HalResource}
import play.api.libs.json.Json._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Pending}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId

trait ClientInvitationsHal {

  protected def agencyLink(invitation: Invitation): Option[String]

  def toHalResource(invitation: Invitation): HalResource = {
    var links = HalLinks(Vector(HalLink("self", routes.ClientInvitationsController.getInvitation(
      MtdItId(invitation.clientId), invitation.id.stringify).url)))

    agencyLink(invitation).foreach(href => links = links ++ HalLink("agency", href))

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", routes.ClientInvitationsController.acceptInvitation(
        MtdItId(invitation.clientId), invitation.id.stringify).url)
      links = links ++ HalLink("reject", routes.ClientInvitationsController.rejectInvitation(
        MtdItId(invitation.clientId), invitation.id.stringify).url)
    }

    HalResource(links, toJson(invitation).as[JsObject])
  }

  private def invitationLinks(invitations: Seq[Invitation]): Vector[HalLink] = {
    invitations.map { i =>
      HalLink("invitations", routes.ClientInvitationsController.getInvitation(
        MtdItId(i.clientId), i.id.stringify).toString)
    }.toVector
  }

  def toHalResource(mtdItId: MtdItId, selfLinkHref: String): HalResource = {
    val selfLink = Vector(HalLink("self", selfLinkHref))
    val invitationsSentLink = Vector(HalLink("received",
      routes.ClientInvitationsController.getInvitations(mtdItId, None).url))
    hal(Json.obj(), selfLink ++ invitationsSentLink, Vector())
  }

  def toHalResource(invitations: Seq[Invitation], mtdItId: MtdItId, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = invitations.map(invitation => toHalResource(invitation)).toVector

    val links = Vector(HalLink("self", routes.ClientInvitationsController.getInvitations(
      mtdItId, status).url)) ++ invitationLinks(invitations)
    hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

}
