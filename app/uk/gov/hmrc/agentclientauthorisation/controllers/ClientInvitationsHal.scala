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
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino

trait ClientInvitationsHal {

  // Why are we not using the ids stored in the various ClientIdTypes? Because of capitalisation discrepancies :(
  private def clientIdType(clientId: ClientId): String = clientId.underlying match {
    case MtdItId(_) => "MTDITID"
    case Nino(_)    => "NI"
    case Vrn(_)     => "VRN"
    case Utr(_)     => "UTR"
    case Urn(_)     => "URN"
    case CgtRef(_)  => "CGTPDRef"
    case PptRef(_)  => "EtmpRegistrationNumber"
    case CbcId(_)   => "cbcId"
    case PlrId(_)   => "PLRID"
  }

  def toHalResource(invitation: Invitation): HalResource = {
    val invitationUrl = routes.ClientInvitationsController
      .getInvitation(clientIdType(invitation.clientId), invitation.clientId.value, invitation.invitationId)

    var links = HalLinks(Vector(HalLink("self", invitationUrl.url)))

    lazy val acceptLink = routes.ClientInvitationsController
      .acceptInvitation(clientIdType(invitation.clientId), invitation.clientId.value, invitation.invitationId)

    lazy val rejectLink = routes.ClientInvitationsController
      .rejectInvitation(clientIdType(invitation.clientId), invitation.clientId.value, invitation.invitationId)

    if (invitation.status == Pending) {
      links = links ++ HalLink("accept", acceptLink.url)
      links = links ++ HalLink("reject", rejectLink.url)
    }

    HalResource(links, toJson(invitation)(Invitation.external.writes).as[JsObject])
  }

  private def invitationLinks(invitations: Seq[Invitation]): Vector[HalLink] =
    invitations.map { i =>
      val link = routes.ClientInvitationsController.getInvitation(clientIdType(i.clientId), i.clientId.value, i.invitationId)
      HalLink("invitations", link.toString)
    }.toVector

  def toHalResource(invitations: Seq[Invitation], clientId: ClientId, status: Option[InvitationStatus]): HalResource = {
    val requestResources: Vector[HalResource] = invitations.map(invitation => toHalResource(invitation)).toVector

    val link = routes.ClientInvitationsController.getInvitations(clientIdType(clientId), clientId.value, status)

    val links = Vector(HalLink("self", link.url)) ++ invitationLinks(invitations)
    hal(Json.obj(), links, Vector("invitations" -> requestResources))
  }

}
