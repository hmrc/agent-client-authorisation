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
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, Invitation, InvitationStatus, Pending}

trait AgencyInvitationsHal {

  protected def reverseRoutes: ReverseAgencyInvitationsRoutes

  protected def agencyLink(invitation: Invitation): Option[String]

  def toHalResource(invitations: List[Invitation], arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]): HalResource = {
    val invitationResources = invitations.map(toHalResource(_, arn)).toVector

    val selfLink = Vector(HalLink("self", reverseRoutes.getSentInvitations(arn, regime, clientId, status).url))
    Hal.hal(Json.obj(), selfLink, Vector("invitations" -> invitationResources))
  }

  def toHalResource(invitation: Invitation, arn: Arn): HalResource = {
    var links = HalLinks(Vector(HalLink("self", reverseRoutes.getSentInvitation(arn, invitation.id.stringify).url)))

    agencyLink(invitation).foreach(href => links = links ++ HalLink("agency", href))

    if (invitation.status == Pending) {
      links = links ++ HalLink("cancel", reverseRoutes.cancelInvitation(arn, invitation.id.stringify).url)
    }
    HalResource(links, toJson(invitation).as[JsObject])
  }

}

trait ReverseAgencyInvitationsRoutes {
  def getSentInvitation(arn:Arn, invitationId:String): Call
  def getSentInvitations(arn:Arn, regime:Option[String], clientId:Option[String], status:Option[InvitationStatus]): Call
  def cancelInvitation(arn: Arn, invitationId:String): Call
}
