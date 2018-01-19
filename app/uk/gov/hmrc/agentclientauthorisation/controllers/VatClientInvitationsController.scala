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

import javax.inject.Inject

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, Vrn}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

class VatClientInvitationsController @Inject()(invitationsService: InvitationsService)
                                              (implicit metrics: Metrics,
                                              authConnector: MicroserviceAuthConnector,
                                              auditService: AuditService)
  extends BaseClientInvitationsController[Vrn](invitationsService, metrics, authConnector, auditService) {

  override val supportedService: Service = Service.Vat

  def getDetailsForClient(vrn: Vrn): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authVrn => getDetailsForClient(ClientIdentifier(vrn), request)
  }

  def acceptInvitation(vrn: Vrn, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authVrn => acceptInvitation(ClientIdentifier(vrn), invitationId)
  }

  def rejectInvitation(vrn: Vrn, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authVrn =>
        forThisClient(ClientIdentifier(vrn)) {
          actionInvitation(ClientIdentifier(vrn), invitationId, invitationsService.rejectInvitation)
        }
  }

  def getInvitation(vrn: Vrn, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authVrn => getInvitation(ClientIdentifier(vrn), invitationId)
  }

  def getInvitations(vrn: Vrn, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request =>
      implicit authVrn => getInvitations(ClientIdentifier(vrn), status)
  }

  def onlyForClients(action: Request[AnyContent] => ClientIdentifier[Vrn] => Future[Result]): Action[AnyContent] =
    super.onlyForClients(Service.Vat, VrnType)(action)

}
