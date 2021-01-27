/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{CgtRef, MtdItId, Urn, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

trait ClientInvitationsHal {

  def toHalResource(invitation: Invitation): HalResource = {
    val invitationUrl = invitation.service match {
      case MtdIt =>
        routes.ClientInvitationsController
          .getInvitation("MTDITID", invitation.clientId.value, invitation.invitationId)
      case PersonalIncomeRecord =>
        routes.ClientInvitationsController.getInvitation("NI", invitation.clientId.value, invitation.invitationId)
      case Vat =>
        routes.ClientInvitationsController.getInvitation("VRN", invitation.clientId.value, invitation.invitationId)
      case Trust =>
        routes.ClientInvitationsController.getInvitation("UTR", invitation.clientId.value, invitation.invitationId)
      case TrustNT =>
        routes.ClientInvitationsController.getInvitation("URN", invitation.clientId.value, invitation.invitationId)
      case CapitalGains =>
        routes.ClientInvitationsController.getInvitation("CGTPDRef", invitation.clientId.value, invitation.invitationId)
    }

    var links = HalLinks(Vector(HalLink("self", invitationUrl.url)))

    lazy val acceptLink = invitation.service match {
      case MtdIt =>
        routes.ClientInvitationsController
          .acceptInvitation("MTDITID", invitation.clientId.value, invitation.invitationId)
      case PersonalIncomeRecord =>
        routes.ClientInvitationsController.acceptInvitation("NI", invitation.clientId.value, invitation.invitationId)
      case Vat =>
        routes.ClientInvitationsController.acceptInvitation("VRN", invitation.clientId.value, invitation.invitationId)
      case Trust =>
        routes.ClientInvitationsController
          .acceptInvitation("UTR", invitation.clientId.value, invitation.invitationId)
      case TrustNT =>
        routes.ClientInvitationsController
          .acceptInvitation("URN", invitation.clientId.value, invitation.invitationId)
      case CapitalGains =>
        routes.ClientInvitationsController
          .acceptInvitation("CGTPDRef", invitation.clientId.value, invitation.invitationId)
    }

    lazy val rejectLink = invitation.service match {
      case MtdIt =>
        routes.ClientInvitationsController
          .rejectInvitation("MTDITID", invitation.clientId.value, invitation.invitationId)
      case PersonalIncomeRecord =>
        routes.ClientInvitationsController.rejectInvitation("NI", invitation.clientId.value, invitation.invitationId)
      case Vat =>
        routes.ClientInvitationsController.rejectInvitation("VRN", invitation.clientId.value, invitation.invitationId)
      case Trust =>
        routes.ClientInvitationsController
          .rejectInvitation("UTR", invitation.clientId.value, invitation.invitationId)
      case TrustNT =>
        routes.ClientInvitationsController
          .rejectInvitation("URN", invitation.clientId.value, invitation.invitationId)
      case CapitalGains =>
        routes.ClientInvitationsController
          .rejectInvitation("CGTPDRef", invitation.clientId.value, invitation.invitationId)
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
        case MtdIt =>
          routes.ClientInvitationsController.getInvitation("MTDITID", i.clientId.value, i.invitationId)
        case PersonalIncomeRecord =>
          routes.ClientInvitationsController.getInvitation("NI", i.clientId.value, i.invitationId)
        case Vat => routes.ClientInvitationsController.getInvitation("VRN", i.clientId.value, i.invitationId)
        case Trust =>
          routes.ClientInvitationsController.getInvitation("UTR", i.clientId.value, i.invitationId)
        case TrustNT =>
          routes.ClientInvitationsController.getInvitation("URN", i.clientId.value, i.invitationId)
        case CapitalGains =>
          routes.ClientInvitationsController.getInvitation("CGTPDRef", i.clientId.value, i.invitationId)
      }
      HalLink("invitations", link.toString)
    }.toVector

  def toHalResource(invitations: Seq[Invitation], clientId: ClientId, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = invitations.map(invitation => toHalResource(invitation)).toVector

    val link = clientId match {
      case clientId @ ClientIdentifier(MtdItId(_)) =>
        routes.ClientInvitationsController.getInvitations("MTDITID", clientId.value, status)
      case clientId @ ClientIdentifier(Nino(_)) =>
        routes.ClientInvitationsController.getInvitations("NI", clientId.value, status)
      case clientId @ ClientIdentifier(Vrn(_)) =>
        routes.ClientInvitationsController.getInvitations("VRN", clientId.value, status)
      case clientId @ ClientIdentifier(Utr(_)) =>
        routes.ClientInvitationsController.getInvitations("UTR", clientId.value, status)
      case clientId @ ClientIdentifier(Urn(_)) =>
        routes.ClientInvitationsController.getInvitations("URN", clientId.value, status)
      case clientId @ ClientIdentifier(CgtRef(_)) =>
        routes.ClientInvitationsController.getInvitations("CGTPDRef", clientId.value, status)
      case other =>
        throw new RuntimeException(s"'toHalResource' not yet implemented for $other ")
    }

    val links = Vector(HalLink("self", link.url)) ++ invitationLinks(invitations)
    hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

}
