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

import javax.inject._

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Service}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, MtdItId}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class MtdItClientInvitationsController @Inject()(invitationsService: InvitationsService)
                                                (implicit metrics: Metrics,
                                            microserviceAuthConnector: MicroserviceAuthConnector,
                                            auditService: AuditService)
  extends BaseClientInvitationsController(invitationsService, metrics, microserviceAuthConnector, auditService) {

  def getDetailsForClient(mtdItId: MtdItId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          getDetailsForClient(mtdItId, request)
        }
  }

  def acceptInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          super.acceptInvitation(mtdItId, invitationId)
        }
  }

  def rejectInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          actionInvitation(mtdItId, invitationId, invitationsService.rejectInvitation)
        }
  }

  def getInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          getInvitation(mtdItId, invitationId)
        }
  }

  def getInvitations(mtdItId: MtdItId, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authMtdItId =>
        forThisClient(mtdItId) {
          getInvitations(mtdItId, status, Service.MtdIt)
        }
  }

  private def forThisClient(mtdItId: MtdItId)(block: => Future[Result])(implicit authMtdItId: MtdItId) =
    if (authMtdItId.value != mtdItId.value)
      Future successful NoPermissionOnClient
    else block

  override protected def agencyLink(invitation: Invitation): Option[String] = None

  def onlyForClients(action: Request[AnyContent] => MtdItId => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.MtdIt, CLIENT_ID_TYPE_MTDITID)(action)(MtdItId.apply)

}
