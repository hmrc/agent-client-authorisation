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

import javax.inject._

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.{JsError, JsSuccess, JsValue}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{InvitationNotFound, NoPermissionOnAgency, invalidInvitationStatus, ClientRegistrationNotFound}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AgentInvitationValidation
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService, StatusUpdateFailure}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

@Singleton
class AgencyInvitationsController @Inject()(override val postcodeService: PostcodeService,
                                            invitationsService: InvitationsService)
                                           (implicit metrics: Metrics,
                                            microserviceAuthConnector: MicroserviceAuthConnector)
  extends AuthConnector(metrics, microserviceAuthConnector) with HalWriter
    with AgentInvitationValidation with AgencyInvitationsHal {

  def createInvitation(givenArn: Arn): Action[AnyContent] = onlyForAgents {
    implicit request =>
      implicit arn =>
        forThisAgency(givenArn) {
          val invitationJson: Option[JsValue] = request.body.asJson
          localWithJsonBody({
            agentInvitation => {
              val reformatClientId = agentInvitation.reformatClientId
              checkForErrors(agentInvitation.copy(clientId = reformatClientId)).flatMap(
                _.fold(makeInvitation(givenArn, agentInvitation))(error => Future successful error))
            }
          }, invitationJson.get)
        }
  }

  private def localWithJsonBody(f: (AgentInvitation) => Future[Result], request: JsValue): Future[Result] =
    Try(request.validate[AgentInvitation]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs)) => Future successful BadRequest(s"Invalid payload: $errs")
      case Failure(e) => Future successful BadRequest(s"could not parse body due to ${e.getMessage}")
    }

  private def makeInvitation(arn: Arn, agentInvitation: AgentInvitation)(implicit hc: HeaderCarrier): Future[Result] = {
    val suppliedClientId = ClientIdentifier(agentInvitation.clientId, agentInvitation.clientIdType)
    (agentInvitation.getService match {
      case Service.MtdIt => invitationsService.translateToMtdItId(agentInvitation.clientId, agentInvitation.clientIdType)
      case _ => Future successful Some(suppliedClientId)
    }) flatMap {
      case None =>
        Future successful ClientRegistrationNotFound
      case Some(taxId) =>
        invitationsService.create(arn, agentInvitation.getService, taxId, agentInvitation.clientPostcode, suppliedClientId).map(
          invitation => Created.withHeaders(location(invitation))
        )
    }
  }

  private def location(invitation: Invitation) =
    LOCATION -> routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.invitationId).url

  def getDetailsForAuthenticatedAgency: Action[AnyContent] = onlyForAgents {
    implicit request =>
      implicit arn =>
        Future successful Ok(toHalResource(arn, request.path))
  }

  def getDetailsForAgency(givenArn: Arn): Action[AnyContent] = onlyForAgents {
    implicit request =>
      implicit arn =>
        forThisAgency(givenArn) {
          Future successful Ok(toHalResource(arn, request.path))
        }
  }

  def getDetailsForAgencyInvitations(arn: Arn): Action[AnyContent] = getDetailsForAgency(arn)

  def getSentInvitations(givenArn: Arn, service: Option[String], clientIdType: Option[String],
                         clientId: Option[String], status: Option[InvitationStatus]): Action[AnyContent] = onlyForAgents {
    implicit request =>
      implicit arn =>
        forThisAgency(givenArn) {
          invitationsService.agencySent(arn, service.map(Service(_)), clientIdType, clientId, status).map { invitations =>
            Ok(toHalResource(invitations, arn, service, clientIdType, clientId, status))
          }
        }
  }

  def getSentInvitation(givenArn: Arn, invitationId: InvitationId): Action[AnyContent] = onlyForAgents {
    implicit request =>
      implicit arn =>
        forThisAgency(givenArn) {
          invitationsService.findInvitation(invitationId).map {
            _.map(invitation => Ok(toHalResource(invitation))) getOrElse InvitationNotFound
          }
        }
  }

  private def forThisAgency(requestedArn: Arn)(block: => Future[Result])(implicit arn: Arn) = {
    if (requestedArn != arn)
      Future successful NoPermissionOnAgency
    else block
  }

  def cancelInvitation(givenArn: Arn, invitationId: InvitationId): Action[AnyContent] = onlyForAgents {
    implicit request =>
      implicit arn =>
        forThisAgency(givenArn) {
          invitationsService.findInvitation(invitationId) flatMap {
            case Some(i) if i.arn == givenArn => invitationsService.cancelInvitation(i) map {
              case Right(_) => NoContent
              case Left(StatusUpdateFailure(_, msg)) => invalidInvitationStatus(msg)
            }
            case None => Future successful InvitationNotFound
            case _ => Future successful NoPermissionOnAgency
          }
        }
  }

  override protected def agencyLink(invitation: Invitation) = None
}
