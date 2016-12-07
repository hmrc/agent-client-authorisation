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
import play.api.libs.json.Json._
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Call
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Pending}

trait ClientInvitationsHal {

  protected def reverseRoutes: ReverseClientInvitationsRoutes

  protected def agencyLink(invitation: Invitation): Option[String]

  def toHalResource(invitation: Invitation): HalResource = {
    var links = HalLinks(Vector(HalLink("self", reverseRoutes.getInvitation(invitation.clientId, invitation.id.stringify).url)))

    agencyLink(invitation).foreach(href => links = links ++ HalLink("agency", href))

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", reverseRoutes.acceptInvitation(invitation.clientId, invitation.id.stringify).url)
      links = links ++ HalLink("reject", reverseRoutes.rejectInvitation(invitation.clientId, invitation.id.stringify).url)
    }

    HalResource(links, toJson(invitation).as[JsObject])
  }

  private def invitationLinks(invitations: Seq[Invitation]): Vector[HalLink] = {
    invitations.map { i => HalLink("invitation", reverseRoutes.getInvitation(i.clientId, i.id.stringify).toString())}.toVector
  }

  def toHalResource(clientId: String, selfLinkHref: String): HalResource = {
    val selfLink = Vector(HalLink("self", selfLinkHref))
    val invitationsSentLink = Vector(HalLink("received", reverseRoutes.getInvitations(clientId, None ).url))
    Hal.hal(Json.obj(), selfLink ++ invitationsSentLink, Vector())
  }

  def toHalResource(invitations: Seq[Invitation], clientId: String, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = invitations.map(invitation => toHalResource(invitation)).toVector

    val links = Vector(HalLink("self", reverseRoutes.getInvitations(clientId, status).url)) ++ invitationLinks(invitations)
    Hal.hal(Json.obj(), links, Vector("invitations"-> requestResources))
  }

}

trait ReverseClientInvitationsRoutes {
  def getInvitation(clientId:String, invitationId:String): Call
  def getInvitations(clientId:String, status:Option[InvitationStatus]): Call
  def acceptInvitation(clientId:String, invitationId:String): Call
  def rejectInvitation(clientId:String, invitationId:String): Call
}
