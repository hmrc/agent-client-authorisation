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
import uk.gov.hmrc.agentclientauthorisation.model.{ClientId, InvitationStatus, NinoType, Service}
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class NiClientInvitationsController @Inject()(invitationsService: InvitationsService)
                                             (implicit metrics: Metrics,
                                              authConnector: MicroserviceAuthConnector,
                                              auditService: AuditService)
  extends BaseClientInvitationsController[Nino](invitationsService, metrics, authConnector, auditService) {

  override val supportedService: Service = Service.PersonalIncomeRecord

  def getDetailsForClient(nino: Nino): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino => getDetailsForClient(ClientId(nino), request)
  }

  def acceptInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino => acceptInvitation(ClientId(nino), invitationId)
  }

  def rejectInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino =>
        forThisClient(ClientId(nino)) {
          actionInvitation(ClientId(nino), invitationId, invitationsService.rejectInvitation)
        }
  }

  def getInvitation(nino: Nino, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino => getInvitation(ClientId(nino), invitationId)
  }

  def getInvitations(nino: Nino, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authNino => getInvitations(ClientId(nino), status)
  }

  def onlyForClients(action: Request[AnyContent] => ClientId[Nino] => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.PersonalIncomeRecord, NinoType.enrolmentId)(action)(Nino.apply)

}
