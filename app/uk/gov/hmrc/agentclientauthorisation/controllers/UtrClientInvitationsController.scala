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
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{Eori, InvitationId, Utr}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.Success

class UtrClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, authConnector) with HalWriter with ClientInvitationsHal {

  implicit val ec: ExecutionContext = ecp.get

  def acceptInvitation(utr: Utr, invitationId: InvitationId): Action[AnyContent] = withUtrEnabledService(invitationId) {
    case Service.NiOrgNotEnrolled =>
      onlyForClients(Service.NiOrgNotEnrolled, EoriType) { implicit request => implicit eori: ClientIdentifier[Eori] =>
        //cross check eori with supplied utr
        //substitute clientId in the invitation
        //call acceptInvitation with Eori
        for {
          updatedInvitationOpt <- invitationsService.updateClientId(invitationId, eori)
          result <- updatedInvitationOpt match {
                     case Some(invitation) =>
                       invitationsService
                         .acceptInvitation(invitation)
                         .andThen {
                           case Success(Right(x)) =>
                             auditService
                               .sendAgentClientRelationshipCreated(invitationId.value, x.arn, eori, invitation.service)
                         }
                         .map(_ => NoContent)
                     case None => Future.successful(NotFound)
                   }
        } yield result
      }
  }

  /*Service.onlyForClients { implicit request => implicit authVrn =>
      acceptInvitation(ClientIdentifier(eori), invitationId)
    }*/

  /*def rejectInvitation(utr: Utr, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      forThisClient(ClientIdentifier(eori)) {
        actionInvitation(ClientIdentifier(eori), invitationId, invitationsService.rejectInvitation)
      }
  }

  def getInvitation(utr: Utr, invitationId: InvitationId): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      getInvitation(ClientIdentifier(eori), invitationId)
  }

  def getInvitations(utr: Utr, status: Option[InvitationStatus]): Action[AnyContent] = onlyForClients {
    implicit request => implicit authVrn =>
      getInvitations(ClientIdentifier(eori), status)
  }*/

  private def withUtrEnabledService(invitationId: InvitationId)(
    f: PartialFunction[Service, Action[AnyContent]]): Action[AnyContent] =
    Service.forInvitationId(invitationId) match {
      case None                        => Action(BadRequest("Invitation ID invalid."))
      case Some(s) if f.isDefinedAt(s) => f(s)
      case Some(_)                     => Action(BadRequest("This invitation cannot be accepted using UTR."))
    }

  override protected def agencyLink(invitation: Invitation): Option[String] = None
}
