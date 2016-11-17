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

import javax.inject.{Inject, Singleton}

import org.joda.time.DateTime.now
import play.api.mvc.Call
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.controllers.{ClientInvitationsHal, HalWriter, ReverseClientInvitationsRoutes, SUPPORTED_REGIME}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.play.microservice.controller.BaseController

@Singleton
class SandboxClientInvitationsController @Inject() (
  override val authConnector: AuthConnector,
  override val agenciesFakeConnector: AgenciesFakeConnector
) extends BaseController with AuthActions with HalWriter with ClientInvitationsHal {

  def acceptInvitation(clientId: String, invitationId: String) = onlyForSaClients { implicit request =>
    NoContent
  }

  def rejectInvitation(clientId: String, invitationId: String) = onlyForSaClients { implicit request =>
    NoContent
  }

  def getInvitation(clientId: String, invitationId: String) = onlyForSaClients { implicit request =>
    Ok(toHalResource(invitation(clientId), clientId))
  }

  def getInvitations(clientId: String, status: Option[InvitationStatus]) = onlyForSaClients { implicit request =>
    Ok(toHalResource(Seq(invitation(clientId), invitation(clientId)), clientId, status))
  }

  private def invitation(clientId: String) = Invitation(
        BSONObjectID.generate,
        Arn("agencyReference"),
        SUPPORTED_REGIME,
        clientId,
        "A11 1AA",
        List(StatusChangeEvent(now(), Pending))
      )


  override protected val reverseRoutes: ReverseClientInvitationsRoutes = ReverseSandboxClientInvitations
  override protected def agencyLink(invitation: Invitation): Option[String] = None
}

private object ReverseSandboxClientInvitations extends ReverseClientInvitationsRoutes {
  override def getInvitation(clientId:String, invitationId:String): Call = routes.SandboxClientInvitationsController.getInvitation(clientId, invitationId)
  override def getInvitations(clientId:String, status:Option[InvitationStatus]): Call = routes.SandboxClientInvitationsController.getInvitations(clientId, status)
  override def acceptInvitation(clientId:String, invitationId:String): Call = routes.SandboxClientInvitationsController.acceptInvitation(clientId, invitationId)
  override def rejectInvitation(clientId:String, invitationId:String): Call = routes.SandboxClientInvitationsController.rejectInvitation(clientId, invitationId)
}
