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

package uk.gov.hmrc.agentclientauthorisation.controllers.sandbox

import javax.inject.Singleton

import org.joda.time.DateTime.now
import play.api.mvc.Action
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.controllers.{routes => prodroutes, _}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class SandboxAgencyInvitationsController extends BaseController with HalWriter with AgencyInvitationsHal {

  def createInvitation(arn: Arn) = Action { implicit request =>
    Created.withHeaders(location(arn, "invitationId"))
  }

  private def location(arn: Arn, invitationId: String) = {
    LOCATION -> prodroutes.AgencyInvitationsController.getSentInvitation(arn, invitationId).url
  }

  def getSentInvitations(arn: Arn, service: Option[String], clientId: Option[String], status: Option[InvitationStatus]) = Action { implicit request =>
    Ok(toHalResource(List(invitation(arn), invitation(arn)), arn, service, None, clientId, status))
  }

  def getDetailsForAuthenticatedAgency() = Action { implicit request =>
    Ok(toHalResource(HardCodedSandboxIds.arn, prodroutes.AgencyInvitationsController.getDetailsForAuthenticatedAgency().url))
  }

  def getDetailsForAgency(arn: Arn) = Action { implicit request =>
    Ok(toHalResource(arn, prodroutes.AgencyInvitationsController.getDetailsForAgency(arn).url))
  }

  def getDetailsForAgencyInvitations(arn: Arn) = Action { implicit request =>
    Ok(toHalResource(arn, prodroutes.AgencyInvitationsController.getDetailsForAgencyInvitations(arn).url))
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
        SUPPORTED_SERVICE,
        MtdItId("clientId"),
        "A11 1AA",
        List(StatusChangeEvent(now(), Pending))
      )

  override protected def agencyLink(invitation: Invitation) = None
}
