/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Eori, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino

trait ClientInvitationsHal {

  protected def agencyLink(invitation: Invitation): Option[String]

  def toHalResource(invitation: Invitation): HalResource = {
    val link = invitation.service match {
      case Service.MtdIt =>
        routes.MtdItClientInvitationsController
          .getInvitation(MtdItId(invitation.clientId.value), invitation.invitationId)
      case Service.PersonalIncomeRecord =>
        routes.NiClientInvitationsController.getInvitation(Nino(invitation.clientId.value), invitation.invitationId)
      case Service.Vat =>
        routes.VatClientInvitationsController.getInvitation(Vrn(invitation.clientId.value), invitation.invitationId)
    }
    var links = HalLinks(Vector(HalLink("self", link.url)))

    agencyLink(invitation).foreach(href => links = links ++ HalLink("agency", href))

    val acceptLink = invitation.service match {
      case Service.MtdIt =>
        routes.MtdItClientInvitationsController
          .acceptInvitation(MtdItId(invitation.clientId.value), invitation.invitationId)
      case Service.PersonalIncomeRecord =>
        routes.NiClientInvitationsController.acceptInvitation(Nino(invitation.clientId.value), invitation.invitationId)
      case Service.Vat =>
        routes.VatClientInvitationsController.acceptInvitation(Vrn(invitation.clientId.value), invitation.invitationId)
    }
    val rejectLink = invitation.service match {
      case Service.MtdIt =>
        routes.MtdItClientInvitationsController
          .rejectInvitation(MtdItId(invitation.clientId.value), invitation.invitationId)
      case Service.PersonalIncomeRecord =>
        routes.NiClientInvitationsController.rejectInvitation(Nino(invitation.clientId.value), invitation.invitationId)
      case Service.Vat =>
        routes.VatClientInvitationsController.rejectInvitation(Vrn(invitation.clientId.value), invitation.invitationId)
    }

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", acceptLink.url)
      links = links ++ HalLink("reject", rejectLink.url)
    }

    HalResource(links, toJson(invitation)(Invitation.external.writes).as[JsObject])
  }

  private def invitationLinks(invitations: Seq[Invitation]): Vector[HalLink] =
    invitations.map { i =>
      val link = i.service match {
        case Service.MtdIt =>
          routes.MtdItClientInvitationsController.getInvitation(MtdItId(i.clientId.value), i.invitationId)
        case Service.PersonalIncomeRecord =>
          routes.NiClientInvitationsController.getInvitation(Nino(i.clientId.value), i.invitationId)
        case Service.Vat => routes.VatClientInvitationsController.getInvitation(Vrn(i.clientId.value), i.invitationId)

      }
      HalLink("invitations", link.toString)
    }.toVector

  def toHalResource(invitations: Seq[Invitation], clientId: ClientId, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = invitations.map(invitation => toHalResource(invitation)).toVector

    val link = clientId match {
      case clientId @ ClientIdentifier(MtdItId(_)) =>
        routes.MtdItClientInvitationsController.getInvitations(clientId.underlying.asInstanceOf[MtdItId], None)
      case clientId @ ClientIdentifier(Nino(_)) =>
        routes.NiClientInvitationsController.getInvitations(clientId.underlying.asInstanceOf[Nino], None)
      case clientId @ ClientIdentifier(Vrn(_)) =>
        routes.VatClientInvitationsController.getInvitations(clientId.underlying.asInstanceOf[Vrn], None)
    }

    val links = Vector(HalLink("self", link.url)) ++ invitationLinks(invitations)
    hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

}
