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
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{Eori, InvitationId}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class EoriClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends BaseClientInvitationsController(invitationsService, metrics, authConnector, auditService) {

  implicit val ec: ExecutionContext = ecp.get

  def acceptInvitation(eori: Eori, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      acceptInvitation(ClientIdentifier(eori), invitationId)
  }

  def rejectInvitation(eori: Eori, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      forThisClient(ClientIdentifier(eori)) {
        actionInvitation(ClientIdentifier(eori), invitationId, invitationsService.rejectInvitation)
      }
  }

  def getInvitation(eori: Eori, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      getInvitation(ClientIdentifier(eori), invitationId)
  }

  def getInvitations(eori: Eori, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      getInvitations(Service.NiOrgEnrolled, ClientIdentifier(eori), status)
  }

  def onlyForClients(action: Request[AnyContent] => ClientIdentifier[Eori] => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.NiOrgEnrolled, EoriType)(action)

}
