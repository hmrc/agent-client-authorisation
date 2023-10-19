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

import play.api.hal.Hal.hal
import play.api.hal.{HalLink, HalLinks, HalResource}
import play.api.libs.json.Json._
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{CbcId, CgtRef, ClientIdentifier, MtdItId, PlrId, PptRef, Urn, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

trait ClientInvitationsHal {

  def clientIdType(invitation: Invitation): String = invitation.service match {
    case MtdIt                => "MTDITID"
    case PersonalIncomeRecord => "NI"
    case Vat                  => "VRN"
    case Trust                => "UTR"
    case TrustNT              => "URN"
    case CapitalGains         => "CGTPDRef"
    case Ppt                  => "EtmpRegistrationNumber"
    case Cbc | CbcNonUk       => "cbcId"
    //TODO WG - how do I know string ????
    case Pillar2 => "pillar2"
  }

  def toHalResource(invitation: Invitation): HalResource = {
    val invitationUrl = routes.ClientInvitationsController
      .getInvitation(clientIdType(invitation), invitation.clientId.value, invitation.invitationId)

    var links = HalLinks(Vector(HalLink("self", invitationUrl.url)))

    lazy val acceptLink = routes.ClientInvitationsController
      .acceptInvitation(clientIdType(invitation), invitation.clientId.value, invitation.invitationId)

    lazy val rejectLink = routes.ClientInvitationsController
      .rejectInvitation(clientIdType(invitation), invitation.clientId.value, invitation.invitationId)

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", acceptLink.url)
      links = links ++ HalLink("reject", rejectLink.url)
    }

    HalResource(links, toJson(invitation)(Invitation.external.writes).as[JsObject])
  }

  private def invitationLinks(invitations: Seq[Invitation]): Vector[HalLink] =
    invitations.map { i =>
      val link = routes.ClientInvitationsController.getInvitation(clientIdType(i), i.clientId.value, i.invitationId)
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
      case clientId @ ClientIdentifier(PptRef(_)) =>
        routes.ClientInvitationsController.getInvitations("EtmpRegistrationNumber", clientId.value, status)
      case clientId @ ClientIdentifier(CbcId(_)) =>
        routes.ClientInvitationsController.getInvitations("cbcId", clientId.value, status)
      case clientId @ ClientIdentifier(PlrId(_)) =>
        routes.ClientInvitationsController.getInvitations("pillar2", clientId.value, status)
      case other =>
        throw new RuntimeException(s"'toHalResource' not yet implemented for $other ")
    }

    val links = Vector(HalLink("self", link.url)) ++ invitationLinks(invitations)
    hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

}
