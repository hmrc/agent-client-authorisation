/*
 * Copyright 2019 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Provider}
import play.api.i18n.MessagesApi
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, InvitationStatus, NinoType, Service}
import uk.gov.hmrc.agentclientauthorisation.service.{ClientNameService, InvitationsService}
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class NiClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends BaseClientInvitationsController(
      invitationsService,
      metrics,
      authConnector,
      auditService) {

  implicit val ec: ExecutionContext = ecp.get

  def acceptInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      implicit val authTaxId: Some[ClientIdentifier[Nino]] = Some(authNino)
      acceptInvitation(ClientIdentifier(nino), invitationId)
  }

  def rejectInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      forThisClient(ClientIdentifier(nino)) {
        actionInvitation(ClientIdentifier(nino), invitationId, invitationsService.rejectInvitation)
      }
  }

  def getInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      getInvitation(ClientIdentifier(nino), invitationId)
  }

  def getInvitations(nino: Nino, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request => implicit authNino =>
      implicit val authTaxId: Some[ClientIdentifier[Nino]] = Some(authNino)
      getInvitations(Service.PersonalIncomeRecord, ClientIdentifier(nino), status)
  }

  def onlyForClients(action: Request[AnyContent] => ClientIdentifier[Nino] => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.PersonalIncomeRecord, NinoType)(action)

}
