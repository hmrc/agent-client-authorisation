/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.mvc._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthActions
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{InvitationNotFound, NoPermissionOnClient, invalidInvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{FriendlyNameService, InvitationsService, StatusUpdateFailure}
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

@Singleton
class ClientInvitationsController @Inject() (appConfig: AppConfig, invitationsService: InvitationsService)(implicit
  metrics: Metrics,
  cc: ControllerComponents,
  authConnector: AuthConnector,
  auditService: AuditService,
  friendlyNameService: FriendlyNameService,
  ec: ExecutionContext
) extends AuthActions(metrics, appConfig, authConnector, cc) with HalWriter with ClientInvitationsHal {

  private val strideRoles = Seq(appConfig.oldStrideEnrolment, appConfig.newStrideEnrolment, appConfig.altStrideEnrolment)

  def acceptInvitation(clientIdType: String, clientId: String, invitationId: InvitationId): Action[AnyContent] =
    AuthorisedClientOrStrideUser(clientIdType, clientId, strideRoles) { implicit request => implicit currentUser =>
      implicit val authTaxId: Option[ClientIdentifier[TaxIdentifier]] = getAuthTaxId(clientIdType)
      acceptInvitation(ClientIdentifier(currentUser.taxIdentifier), invitationId)
    }

  def rejectInvitation(clientIdType: String, clientId: String, invitationId: InvitationId): Action[AnyContent] =
    AuthorisedClientOrStrideUser(clientIdType, clientId, strideRoles) { implicit request => implicit currentUser =>
      implicit val authTaxId: Option[ClientIdentifier[TaxIdentifier]] = getAuthTaxId(clientIdType)
      rejectInvitation(ClientIdentifier(currentUser.taxIdentifier), invitationId)
    }

  private def getAuthTaxId(clientIdType: String)(implicit currentUser: CurrentUser): Option[ClientIdentifier[TaxIdentifier]] = clientIdType match {
    // TODO can anyone explain the logic below? Why do we accept any supported id type except Nino? Can this be rewritten more flexibly?
    case "MTDITID" | "UTR" | "URN" | "VRN" | "CGTPDRef" | "EtmpRegistrationNumber" | "cbcId" | "PLRID"
        if currentUser.credentials.providerType == "GovernmentGateway" =>
      Some(ClientIdentifier(currentUser.taxIdentifier))
    case _ => None
  }

  def getInvitation(clientIdType: String, clientId: String, invitationId: InvitationId): Action[AnyContent] =
    validateClientId(clientIdType, clientId) match {
      case Right((service, taxIdentifier)) =>
        onlyForClients(service, getType(clientIdType)) { _ => implicit authTaxId =>
          getInvitation(ClientIdentifier(taxIdentifier), invitationId)
        }

      case Left(r) => Action.async(Future.successful(r))
    }

  private def getType(clientIdType: String): ClientIdType[TaxIdentifier] =
    clientIdType match {
      case "NI"                     => NinoType
      case "MTDITID"                => MtdItIdType
      case "VRN"                    => VrnType
      case "UTR"                    => UtrType
      case "URN"                    => UrnType
      case "CGTPDRef"               => CgtRefType
      case "EtmpRegistrationNumber" => PptRefType
      case "cbcId"                  => CbcIdType
      case "PLRID"                  => PlrIdType
    }

  def getInvitations(clientIdType: String, identifier: String, status: Option[InvitationStatus]): Action[AnyContent] =
    AuthorisedClientOrStrideUser(clientIdType, identifier, strideRoles) { implicit request => implicit currentUser =>
      implicit val authTaxId: Option[ClientIdentifier[TaxIdentifier]] =
        if (currentUser.credentials.providerType == "GovernmentGateway")
          Some(ClientIdentifier(currentUser.taxIdentifier))
        else None
      val taxId = ClientIdentifier(currentUser.taxIdentifier)
      forThisClientOrStride(taxId) {
        invitationsService
          .updateAltItsaForNino(taxId) // TODO!! Why do we have a side-effect in a GET call?!?
          .flatMap { _ =>
            val possibleServices = currentUser.service match {
              /* TODO [CBC Onboarding] Forced ugly hack. We really need to change the endpoints to include the explicit
                 service rather than rely on just the client id type (which can be ambiguous as in the case of CBC) */
              case Service.Cbc | Service.CbcNonUk    => Seq(Service.Cbc, Service.CbcNonUk)
              case Service.MtdIt | Service.MtdItSupp => Seq(Service.MtdIt, Service.MtdItSupp)
              case svc                               => Seq(svc)
            }
            invitationsService.clientsReceived(possibleServices, taxId, status) map (results => Ok(toHalResource(results, taxId, status)))
          }
      }
    }

  private def acceptInvitation[T <: TaxIdentifier](
    clientId: ClientIdentifier[T],
    invitationId: InvitationId
  )(implicit ec: ExecutionContext, hc: HeaderCarrier, request: Request[Any], authTaxId: Option[ClientIdentifier[T]]): Future[Result] =
    forThisClientOrStride(clientId) {
      actionInvitation(
        clientId,
        invitationId,
        invitation =>
          invitationsService.acceptInvitation(invitation).andThen { case Success(invitation) =>
            val isAltItsa = invitation.service == MtdIt && invitation.clientId == invitation.suppliedClientId
            for {
              _ <- auditService
                     .sendAgentClientRelationshipCreated(invitation.invitationId.value, invitation.arn, clientId, invitation.service, isAltItsa)
              // APB-6204: Update the friendly name in EACD
              _ <- friendlyNameService.updateFriendlyName(invitation)
            } yield ()
          }
      )
    }

  private def rejectInvitation[T <: TaxIdentifier](clientId: ClientIdentifier[T], invitationId: InvitationId)(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier,
    authTaxId: Option[ClientIdentifier[T]]
  ): Future[Result] =
    forThisClientOrStride(clientId) {
      actionInvitation(
        clientId,
        invitationId,
        invitation => invitationsService.rejectInvitation(invitation)
      )
    }

  private def getInvitation[T <: TaxIdentifier](clientId: ClientIdentifier[T], invitationId: InvitationId)(implicit
    ec: ExecutionContext,
    authTaxId: ClientIdentifier[T]
  ): Future[Result] =
    forThisClient(clientId) {
      invitationsService.findInvitation(invitationId).map {
        case Some(x) if matchClientIdentifiers(x.clientId, clientId) => Ok(toHalResource(x))
        case None                                                    => InvitationNotFound
        case _                                                       => NoPermissionOnClient
      }
    }

  private def actionInvitation[T <: TaxIdentifier](
    clientId: ClientIdentifier[T],
    invitationId: InvitationId,
    action: Invitation => Future[Invitation]
  )(implicit ec: ExecutionContext): Future[Result] =
    invitationsService.findInvitation(invitationId) flatMap {
      case Some(invitation) if matchClientIdentifiers(invitation.clientId, clientId) =>
        action(invitation).map(_ => NoContent).recoverWith { case StatusUpdateFailure(_, msg) =>
          Future successful invalidInvitationStatus(msg)
        }
      case None => Future successful InvitationNotFound
      case _    => Future successful NoPermissionOnClient
    }

  private def forThisClient[T <: TaxIdentifier](
    taxId: ClientIdentifier[T]
  )(block: => Future[Result])(implicit authTaxId: ClientIdentifier[T]): Future[Result] =
    if (authTaxId.value.replaceAll("\\s", "") != taxId.value.replaceAll("\\s", ""))
      Future successful NoPermissionOnClient
    else
      block

  private def forThisClientOrStride[T <: TaxIdentifier](
    taxId: ClientIdentifier[T]
  )(block: => Future[Result])(implicit authTaxId: Option[ClientIdentifier[T]]): Future[Result] =
    authTaxId match {
      case None => block
      case Some(authTaxIdentifier) if authTaxIdentifier.value.replaceAll("\\s", "") == taxId.value.replaceAll("\\s", "") =>
        block
      case _ => Future successful NoPermissionOnClient
    }

  protected def matchClientIdentifiers[T <: TaxIdentifier](invitationClientId: ClientId, usersClientId: ClientIdentifier[T]): Boolean =
    if (invitationClientId == usersClientId) true
    else invitationClientId.value.replaceAll("\\s", "") == usersClientId.value.replaceAll("\\s", "")

}
