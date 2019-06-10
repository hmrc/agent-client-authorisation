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
import play.api.i18n.{Messages, MessagesApi}
import play.api.i18n.I18nSupport
import play.api.mvc.{Controller, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, AuthActions, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{ClientNameService, InvitationsService, StatusUpdateFailure}
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

abstract class BaseClientInvitationsController(
  invitationsService: InvitationsService,
  metrics: Metrics,
  authConnector: AuthConnector,
  emailConnector: EmailConnector,
  asaConnector: AgentServicesAccountConnector,
  clientNameService: ClientNameService,
  auditService: AuditService)
    extends AuthActions(metrics, authConnector) with HalWriter with ClientInvitationsHal {

  protected def acceptInvitation[T <: TaxIdentifier](clientId: ClientIdentifier[T], invitationId: InvitationId)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    authTaxId: Option[ClientIdentifier[T]]): Future[Result] =
    forThisClientOrStride(clientId) {
      actionInvitation(
        clientId,
        invitationId,
        invitation =>
          invitationsService.acceptInvitation(invitation).andThen {
            case Success(Right(x)) =>
              auditService.sendAgentClientRelationshipCreated(invitationId.value, x.arn, clientId, invitation.service)
        }
      )
    }

  protected def getInvitation[T <: TaxIdentifier](clientId: ClientIdentifier[T], invitationId: InvitationId)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any],
    authTaxId: ClientIdentifier[T]): Future[Result] =
    forThisClient(clientId) {
      invitationsService.findInvitation(invitationId).map {
        case Some(x) if matchClientIdentifiers(x.clientId, clientId) => Ok(toHalResource(x))
        case None                                                    => InvitationNotFound
        case _                                                       => NoPermissionOnClient
      }
    }
  protected def getInvitations[T <: TaxIdentifier](
    supportedService: Service,
    taxId: ClientIdentifier[T],
    status: Option[InvitationStatus])(
    implicit ec: ExecutionContext,
    authTaxId: Option[ClientIdentifier[T]]): Future[Result] =
    forThisClientOrStride(taxId) {
      invitationsService.clientsReceived(supportedService, taxId, status) map (results =>
        Ok(toHalResource(results, taxId, status)))
    }

  protected def actionInvitation[T <: TaxIdentifier](
    clientId: ClientIdentifier[T],
    invitationId: InvitationId,
    action: Invitation => Future[Either[StatusUpdateFailure, Invitation]])(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any]): Future[Result] =
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if matchClientIdentifiers(invitation.clientId, clientId) =>
        action(invitation) map {
          case Right(_)                          => NoContent
          case Left(StatusUpdateFailure(_, msg)) => invalidInvitationStatus(msg)
        }
      case None => Future successful InvitationNotFound
      case _    => Future successful NoPermissionOnClient
    }

  override protected def agencyLink(invitation: Invitation): Option[String] = None

  protected def forThisClient[T <: TaxIdentifier](taxId: ClientIdentifier[T])(
    block: => Future[Result])(implicit ec: ExecutionContext, authTaxId: ClientIdentifier[T]) =
    if (authTaxId.value.replaceAll("\\s", "") != taxId.value.replaceAll("\\s", ""))
      Future successful NoPermissionOnClient
    else
      block

  protected def forThisClientOrStride[T <: TaxIdentifier](taxId: ClientIdentifier[T])(
    block: => Future[Result])(implicit ec: ExecutionContext, authTaxId: Option[ClientIdentifier[T]]) =
    authTaxId match {
      case None => block
      case Some(authTaxIdentifier)
          if authTaxIdentifier.value.replaceAll("\\s", "") == authTaxIdentifier.value.replaceAll("\\s", "") =>
        block
      case _ => Future successful NoPermissionOnClient
    }

  protected def matchClientIdentifiers[T <: TaxIdentifier](
    invitationClientId: ClientId,
    usersClientId: ClientIdentifier[T]): Boolean =
    if (invitationClientId == usersClientId) true
    else invitationClientId.value.replaceAll("\\s", "") == usersClientId.value.replaceAll("\\s", "")
}
