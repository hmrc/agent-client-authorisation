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

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, Invitation, InvitationStatus, Service}
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, StatusUpdateFailure}
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

abstract class BaseClientInvitationsController[T <: TaxIdentifier](invitationsService: InvitationsService,
                                      metrics: Metrics,
                                      authConnector: MicroserviceAuthConnector,
                                      auditService: AuditService) extends AuthConnector(metrics, authConnector)
                                                                  with HalWriter with ClientInvitationsHal {

  val supportedService: Service

  protected def getDetailsForClient(clientId: ClientIdentifier[T], request: Request[AnyContent])
                                   (implicit ec: ExecutionContext, authTaxId: ClientIdentifier[T]) = forThisClient(clientId) {
      Future successful Ok(toHalResource(clientId, request.path))
  }

  protected def acceptInvitation(clientId: ClientIdentifier[T], invitationId: InvitationId)
                                (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any], authTaxId: ClientIdentifier[T]) =
    forThisClient(clientId) {
      actionInvitation(clientId, invitationId, invitation => invitationsService.acceptInvitation(invitation).andThen {
        case Success(Right(x)) =>
          auditService.sendAgentClientRelationshipCreated(invitationId.value, x.arn, clientId, invitation.service)
      })
    }

  protected def getInvitation(clientId: ClientIdentifier[T], invitationId: InvitationId)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any], authTaxId: ClientIdentifier[T]) = {

    forThisClient(clientId) {
      invitationsService.findInvitation(invitationId).map {
        case Some(x) if x.clientId == clientId => Ok(toHalResource(x))
        case None => InvitationNotFound
        case _ => NoPermissionOnClient
      }
    }
  }
  protected def getInvitations(taxId: ClientIdentifier[T], status: Option[InvitationStatus])
                    (implicit ec: ExecutionContext, authTaxId: ClientIdentifier[T]) = forThisClient(taxId) {
    invitationsService.clientsReceived(supportedService, taxId, status) map (results => Ok(toHalResource(results, taxId, status)))
  }

  protected def actionInvitation(clientId: ClientIdentifier[T], invitationId: InvitationId,
                                 action: Invitation => Future[Either[StatusUpdateFailure, Invitation]])
                                (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if invitation.clientId == clientId =>
        action(invitation) map {
          case Right(_) => NoContent
          case Left(StatusUpdateFailure(_, msg)) => invalidInvitationStatus(msg)
        }
      case None => Future successful InvitationNotFound
      case _ => Future successful NoPermissionOnClient
    }
  }

  override protected def agencyLink(invitation: Invitation): Option[String] = None

  protected def forThisClient(taxId: ClientIdentifier[T])(block: => Future[Result])
                             (implicit ec: ExecutionContext, authTaxId: ClientIdentifier[T]) =
    if (authTaxId.value != taxId.value) Future successful NoPermissionOnClient else block


}
