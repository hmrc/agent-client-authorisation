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
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Pending, Service}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId

trait ClientInvitationsHal {

  protected def agencyLink(invitation: Invitation): Option[String]

  def toHalResource(invitation: Invitation): HalResource = {
    val link = invitation.service match {
      case Service.MtdIt => routes.MtdItClientInvitationsController.getInvitation(MtdItId(invitation.clientId), invitation.invitationId)
      case Service.PersonalIncomeRecord  => routes.NiClientInvitationsController.getInvitation(Nino(invitation.clientId), invitation.invitationId)
    }
    var links = HalLinks(Vector(HalLink("self", link.url)))

    agencyLink(invitation).foreach(href => links = links ++ HalLink("agency", href))

    val acceptLink = invitation.service match {
        case Service.MtdIt => routes.MtdItClientInvitationsController.acceptInvitation(MtdItId(invitation.clientId), invitation.invitationId)
        case Service.PersonalIncomeRecord => routes.NiClientInvitationsController.acceptInvitation(Nino(invitation.clientId), invitation.invitationId)
    }
    val rejectLink = invitation.service match {
      case Service.MtdIt => routes.MtdItClientInvitationsController.rejectInvitation(MtdItId(invitation.clientId), invitation.invitationId)
      case Service.PersonalIncomeRecord => routes.NiClientInvitationsController.rejectInvitation(Nino(invitation.clientId), invitation.invitationId)
    }

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", acceptLink.url)
      links = links ++ HalLink("reject", rejectLink.url)
    }

    HalResource(links, toJson(invitation).as[JsObject])
  }

  private def invitationLinks(invitations: Seq[Invitation]): Vector[HalLink] = {
    invitations.map { i =>
      val link = i.service match {
        case Service.MtdIt => routes.MtdItClientInvitationsController.getInvitation(MtdItId(i.clientId), i.invitationId)
        case Service.PersonalIncomeRecord => routes.NiClientInvitationsController.getInvitation(Nino(i.clientId), i.invitationId)
      }
      HalLink("invitations", link.toString)
    }.toVector
  }

  def toHalResource(taxId: TaxIdentifier, selfLinkHref: String): HalResource = {
    val selfLink = Vector(HalLink("self", selfLinkHref))
    val link = taxId match {
      case mtdItId @ MtdItId(_) => routes.MtdItClientInvitationsController.getInvitations(mtdItId, None)
      case nino @ Nino(_) => routes.NiClientInvitationsController.getInvitations(nino, None)
    }
    hal(Json.obj(), selfLink ++ Vector(HalLink("received", link.url)), Vector())
  }

  def toHalResource(invitations: Seq[Invitation], taxId: TaxIdentifier, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = invitations.map(invitation => toHalResource(invitation)).toVector

    val link = taxId match {
      case mtdItId @ MtdItId(_) => routes.MtdItClientInvitationsController.getInvitations(mtdItId, status)
      case nino @ Nino(_) => routes.NiClientInvitationsController.getInvitations(nino, None)
    }

    val links = Vector(HalLink("self", link.url)) ++ invitationLinks(invitations)
    hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

}
