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

import com.kenshoo.play.metrics.Metrics
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Service}
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, StatusUpdateFailure}
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

// TODO we've modelled clientId using TaxIdentifier, consider using our own type which wont have the large number
// TODO of sub types that we don't support
abstract class BaseClientInvitationsController[T <: TaxIdentifier](invitationsService: InvitationsService,
                                      metrics: Metrics,
                                      authConnector: MicroserviceAuthConnector,
                                      auditService: AuditService) extends AuthConnector(metrics, authConnector)
                                                                  with HalWriter with ClientInvitationsHal {

  val supportedService: Service

  protected def getDetailsForClient(taxId: T, request: Request[AnyContent])
                                   (implicit ec: ExecutionContext, authTaxId: T) = forThisClient(taxId) {
      Future successful Ok(toHalResource(taxId, request.path))
  }

  protected def acceptInvitation(taxId: T, invitationId: InvitationId)
                                (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any], authTaxId: T) =
    forThisClient(taxId) {
      actionInvitation(taxId, invitationId, invitation => invitationsService.acceptInvitation(invitation).andThen {
        case Success(Right(x)) =>
          auditService.sendAgentClientRelationshipCreated(invitationId.value, x.arn, taxId, invitation.service)
      })
    }

  protected def getInvitation(taxId: T, invitationId: InvitationId)
                   (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any], authTaxId: T) = {

    forThisClient(taxId) {
      invitationsService.findInvitation(invitationId).map {
        case Some(x) if x.clientId == taxId.value => Ok(toHalResource(x))
        case None => InvitationNotFound
        case _ => NoPermissionOnClient
      }
    }
  }
  protected def getInvitations(taxId: T, status: Option[InvitationStatus])
                    (implicit ec: ExecutionContext, authTaxId: T) = forThisClient(taxId) {
    invitationsService.clientsReceived(supportedService, taxId, status) map (results => Ok(toHalResource(results, taxId, status)))
  }

  protected def actionInvitation(taxId: T, invitationId: InvitationId,
                                 action: Invitation => Future[Either[StatusUpdateFailure, Invitation]])
                                (implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any]) = {
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation)
        if invitation.clientId == taxId.value =>
        action(invitation) map {
          case Right(_) => NoContent
          case Left(StatusUpdateFailure(_, msg)) => invalidInvitationStatus(msg)
        }
      case None => Future successful InvitationNotFound
      case _ => Future successful NoPermissionOnClient
    }
  }

  override protected def agencyLink(invitation: Invitation): Option[String] = None

  protected def forThisClient(taxId: T)(block: => Future[Result])(implicit ec: ExecutionContext, authTaxId: T) =
    if (authTaxId.value != taxId.value) Future successful NoPermissionOnClient else block


}
