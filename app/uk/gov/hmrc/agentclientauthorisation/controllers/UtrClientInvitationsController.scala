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
import play.api.mvc._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.NiExemptionRegistrationConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{Eori, InvitationId, Utr}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.Success

class UtrClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  niExemptionRegistrationConnector: NiExemptionRegistrationConnector,
  ecp: Provider[ExecutionContextExecutor])
    extends BaseClientInvitationsController(invitationsService, metrics, authConnector, auditService) {

  implicit val ec: ExecutionContext = ecp.get

  def acceptInvitation(utr: Utr, invitationId: InvitationId): Action[AnyContent] = withUtrEnabledService(invitationId) {
    case service @ Service.NiOrgNotEnrolled =>
      onlyForClients(service, EoriType) { implicit request => implicit eori: ClientIdentifier[Eori] =>
        actionInvitation(
          ClientIdentifier(utr),
          invitationId, { invitation =>
            for {
              _ <- invitationsService.update(invitation.copy(clientId = eori))
              result <- invitationsService.acceptInvitation(invitation).andThen {
                         case Success(Right(x)) =>
                           auditService
                             .sendAgentClientRelationshipCreated(invitationId.value, x.arn, eori, invitation.service)
                       }
            } yield result
          }
        )
      }
  }

  def rejectInvitation(utr: Utr, invitationId: InvitationId): Action[AnyContent] = withUtrEnabledService(invitationId) {
    case service @ Service.NiOrgNotEnrolled =>
      onlyForClients(service, EoriType) { implicit request => implicit eori: ClientIdentifier[Eori] =>
        actionInvitation(
          ClientIdentifier(utr),
          invitationId, { invitation =>
            for {
              _      <- invitationsService.update(invitation.copy(clientId = eori))
              result <- invitationsService.rejectInvitation(invitation)
            } yield result
          }
        )
      }
  }

  private def withUtrEnabledService(invitationId: InvitationId)(
    f: PartialFunction[Service, Action[AnyContent]]): Action[AnyContent] =
    Service.forInvitationId(invitationId) match {
      case None                        => Action(BadRequest("Invitation ID invalid."))
      case Some(s) if f.isDefinedAt(s) => f(s)
      case Some(_)                     => Action(BadRequest("This invitation type cannot be accepted or rejected using UTR."))
    }

}
