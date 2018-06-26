/*
 * Copyright 2018 HM Revenue & Customs
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
class SandboxClientInvitationsController extends BaseController with HalWriter with ClientInvitationsHal {

  //  def getDetailsForAuthenticatedClient() = Action { implicit request =>
  //    Ok(toHalResource(HardCodedSandboxIds.clientId.value, prodroutes.ClientInvitationsController.getDetailsForAuthenticatedClient().url))
  //  }
  //
  //  def getDetailsForClient(clientId: String) = Action { implicit request =>
  //    Ok(toHalResource(clientId, prodroutes.ClientInvitationsController.getDetailsForClient(clientId).url))
  //  }
  //
  //  def acceptInvitation(clientId: String, invitationId: String) = Action { implicit request =>
  //    NoContent
  //  }
  //
  //  def rejectInvitation(clientId: String, invitationId: String) = Action { implicit request =>
  //    NoContent
  //  }
  //
  //  def getInvitation(clientId: String, invitationId: String) = Action { implicit request =>
  //    Ok(toHalResource(invitation(clientId)))
  //  }
  //
  //  def getInvitations(clientId: String, status: Option[InvitationStatus]) = Action { implicit request =>
  //    Ok(toHalResource(Seq(invitation(clientId), invitation(clientId)), MtdItId(clientId), status))
  //  }
  //
  //  private def invitation(clientId: String) = Invitation(
  //        BSONObjectID.generate,
  //        Arn("agencyReference"),
  //        SUPPORTED_SERVICE,
  //        MtdItId(clientId),
  //        "A11 1AA",
  //        List(StatusChangeEvent(now(), Pending))
  //      )
  //
  override protected def agencyLink(invitation: Invitation): Option[String] =
    None
}
