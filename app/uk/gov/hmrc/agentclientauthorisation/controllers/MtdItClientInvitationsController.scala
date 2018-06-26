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

package uk.gov.hmrc.agentclientauthorisation.controllers

import javax.inject._

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, InvitationStatus, MtdItIdType, Service}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, MtdItId}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class MtdItClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService)
    extends BaseClientInvitationsController[MtdItId](invitationsService, metrics, authConnector, auditService) {

  override val supportedService: Service = Service.MtdIt

  def getDetailsForClient(mtdItId: MtdItId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authMtdItId =>
      getDetailsForClient(ClientIdentifier(mtdItId), request)
  }

  def acceptInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authMtdItId =>
      acceptInvitation(ClientIdentifier(mtdItId), invitationId)
  }

  def rejectInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authMtdItId =>
      forThisClient(ClientIdentifier(mtdItId)) {
        actionInvitation(ClientIdentifier(mtdItId), invitationId, invitationsService.rejectInvitation)
      }
  }

  def getInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authMtdItId =>
      getInvitation(ClientIdentifier(mtdItId), invitationId)
  }

  def getInvitations(mtdItId: MtdItId, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request => implicit authMtdItId =>
      getInvitations(ClientIdentifier(mtdItId), status)
  }

  def onlyForClients(action: Request[AnyContent] => ClientIdentifier[MtdItId] => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.MtdIt, MtdItIdType)(action)

}
