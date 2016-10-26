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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc.Result
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatus
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class ClientInvitationsController(invitationsRepository: InvitationsRepository,
                                  override val authConnector: AuthConnector,
                                  override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions {

  def acceptInvitation(clientRegimeId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    invitationsRepository.findById(BSONObjectID(invitationId)) flatMap {
      case Some(invitation) if invitation.clientRegimeId == clientRegimeId => changeInvitationStatus(invitationId, model.Accepted)
      case None => Future successful NotFound
      case _ => Future successful Forbidden
    }
  }

  def rejectInvitation(clientRegimeId: String, invitationId: String) = onlyForSaClients.async { implicit request =>
    invitationsRepository.findById(BSONObjectID(invitationId)) flatMap {
      case Some(invitation) if invitation.clientRegimeId == clientRegimeId => changeInvitationStatus(invitationId, model.Rejected)
      case None => Future successful NotFound
      case _ => Future successful Forbidden
    }
  }

  private def changeInvitationStatus(invitationId: String, status: InvitationStatus): Future[Result] = {
    invitationsRepository.update(BSONObjectID(invitationId), status) map (_ => NoContent)
  }
}
