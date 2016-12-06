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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import javax.inject.Singleton

import org.joda.time.DateTime.now
import play.api.mvc.Action
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.controllers.{AgencyInvitationsHal, HalWriter, ReverseAgencyInvitationsRoutes, SUPPORTED_REGIME}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class SandboxAgencyInvitationsController extends BaseController with HalWriter with AgencyInvitationsHal {

  def createInvitation(arn: Arn) = Action { implicit request =>
    Created.withHeaders(location(arn, "invitationId"))
  }

  private def location(arn: Arn, invitationId: String) = {
    LOCATION -> routes.SandboxAgencyInvitationsController.getSentInvitation(arn, invitationId).url
  }

  def getSentInvitations(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]) = Action { implicit request =>
    Ok(toHalResource(List(invitation(arn), invitation(arn)), arn, regime, clientId, status))
  }

  def getDetailsForAuthenticatedAgency() = Action { implicit request =>
    Ok(toHalResource(HardCodedSandboxIds.arn, request.path))
  }

  def getDetailsForAgency(arn: Arn) = Action { implicit request =>
    Ok(toHalResource(arn, request.path))
  }

  def getSentInvitation(arn: Arn, invitationId: String) = Action { implicit request =>
    Ok(toHalResource(invitation(arn)))
  }

  def cancelInvitation(arn: Arn, invitation: String) = Action { implicit request =>
    NoContent
  }

  private def invitation(arn: Arn) = Invitation(
        BSONObjectID.generate,
        arn,
        SUPPORTED_REGIME,
        "clientId",
        "A11 1AA",
        List(StatusChangeEvent(now(), Pending))
      )

  override protected def reverseRoutes: ReverseAgencyInvitationsRoutes = ReverseAgencyInvitations

  override protected def agencyLink(invitation: Invitation) = None
}


private object ReverseAgencyInvitations extends ReverseAgencyInvitationsRoutes {
  override def getSentInvitation(arn: Arn, invitationId: String) =
    routes.SandboxAgencyInvitationsController.getSentInvitation(arn, invitationId)

  override def getSentInvitations(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]) =
    routes.SandboxAgencyInvitationsController.getSentInvitations(arn, regime, clientId, status)

  override def cancelInvitation(arn: Arn, invitationId: String) =
    routes.SandboxAgencyInvitationsController.cancelInvitation(arn, invitationId)
}
