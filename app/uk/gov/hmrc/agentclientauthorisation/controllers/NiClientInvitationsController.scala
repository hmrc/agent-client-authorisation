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

import javax.inject.Inject

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.{MicroserviceAuthConnector, _}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Service}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class NiClientInvitationsController @Inject()(invitationsService: InvitationsService)
                                             (implicit metrics: Metrics,
                                              authConnector: MicroserviceAuthConnector,
                                              auditService: AuditService)
  extends BaseClientInvitationsController(invitationsService, metrics, authConnector, auditService) {

  def getDetailsForClient(nino: Nino): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit autNino =>
        forThisClient(nino) {
          getDetailsForClient(nino, request)
        }
  }

  def acceptInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino =>
        forThisClient(nino) {
          acceptInvitation(nino, invitationId)
        }
  }

  def rejectInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino =>
        forThisClient(nino) {
          actionInvitation(nino, invitationId, invitationsService.rejectInvitation)
        }
  }

  def getInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino =>
        forThisClient(nino) {
          getInvitation(nino, invitationId)
        }
  }

  def getInvitations(nino: Nino, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino =>
        forThisClient(nino) {
          getInvitations(nino, status, Service.PersonalIncomeRecord)
        }
  }

  override protected def agencyLink(invitation: Invitation): Option[String] = None

  private def forThisClient(nino: Nino)(block: => Future[Result])(implicit authNino: Nino) =
    if (authNino.value != nino.value) Future successful NoPermissionOnClient else block

  def onlyForClients(action: Request[AnyContent] => Nino => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.PersonalIncomeRecord, CLIENT_ID_TYPE_NINO)(action)(Nino.apply)

}
