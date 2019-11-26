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
import javax.inject._
import org.joda.time.LocalDate
import play.api.Logger
import play.api.http.HeaderNames
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, AuthActions, DesConnector, MicroserviceAuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{ClientRegistrationNotFound, DateOfBirthDoesNotMatch, InvitationNotFound, NoPermissionOnAgency, VatRegistrationDateDoesNotMatch, invalidInvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AgentInvitationValidation
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AgencyInvitationsController @Inject()(
  @Named("agent-invitations-frontend.external-url") invitationsFrontendBaseUrl: String,
  postcodeService: PostcodeService,
  invitationsService: InvitationsService,
  knownFactsCheckService: KnownFactsCheckService,
  agentLinkService: AgentLinkService,
  desConnector: DesConnector,
  agentServicesAccountConnector: AgentServicesAccountConnector,
  agentCacheProvider: AgentCacheProvider)(
  implicit
  metrics: Metrics,
  cc: ControllerComponents,
  microserviceAuthConnector: MicroserviceAuthConnector,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, microserviceAuthConnector, cc) with HalWriter with AgentInvitationValidation
    with AgencyInvitationsHal {

  implicit val ec: ExecutionContext = ecp.get

  private val trustCache = agentCacheProvider.trustResponseCache
  private val cgtCache = agentCacheProvider.cgtSubscriptionCache

  def createInvitation(givenArn: Arn): Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    forThisAgency(givenArn) {
      val invitationJson: Option[JsValue] = request.body.asJson
      localWithJsonBody(
        { agentInvitation =>
          val normalizedClientId = AgentInvitation.normalizeClientId(agentInvitation.clientId)
          checkForErrors(agentInvitation.copy(clientId = normalizedClientId))
            .flatMap(_.fold(makeInvitation(givenArn, agentInvitation))(error => Future successful error))

        },
        invitationJson.get
      )
    }
  }

  def getInvitationUrl(givenArn: Arn, clientType: String) = onlyForAgents { implicit request => implicit arn =>
    forThisAgency(givenArn) {
      for {
        result <- agentLinkService
                   .getInvitationUrl(arn, clientType)
                   .map(link => Created.withHeaders(HeaderNames.LOCATION -> link))
      } yield result
    }
  }

  def cancelInvitation(givenArn: Arn, invitationId: InvitationId): Action[AnyContent] = onlyForAgents {
    implicit request => implicit arn =>
      forThisAgency(givenArn) {
        invitationsService.findInvitation(invitationId) flatMap {
          case Some(i) if i.arn == givenArn =>
            invitationsService.cancelInvitation(i) map {
              case Right(_)                          => NoContent
              case Left(StatusUpdateFailure(_, msg)) => invalidInvitationStatus(msg)
            }
          case None => Future successful InvitationNotFound
          case _    => Future successful NoPermissionOnAgency
        }
      }
  }

  private def localWithJsonBody(f: AgentInvitation => Future[Result], request: JsValue): Future[Result] =
    Try(request.validate[AgentInvitation]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs))         => Future successful BadRequest(s"Invalid payload: $errs")
      //Marianne 8/2/2019: Is this Failure case even possible?
      case Failure(e) => Future successful BadRequest(s"could not parse body due to ${e.getMessage}")
    }

  private def makeInvitation(arn: Arn, agentInvitation: AgentInvitation)(implicit hc: HeaderCarrier): Future[Result] = {
    val suppliedClientId = ClientIdentifier(agentInvitation.clientId, agentInvitation.clientIdType)
    (agentInvitation.getService match {
      case MtdIt =>
        invitationsService.translateToMtdItId(agentInvitation.clientId, agentInvitation.clientIdType)
      case _ => Future successful Some(suppliedClientId)
    }) flatMap {
      case None =>
        Future successful ClientRegistrationNotFound
      case Some(taxId) =>
        invitationsService
          .create(arn, agentInvitation.clientType, agentInvitation.getService, taxId, suppliedClientId)
          .map(invitation => Created.withHeaders(location(invitation): _*))
    }
  }

  private def location(invitation: Invitation) = Seq(
    LOCATION       -> routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.invitationId).url,
    "InvitationId" -> invitation.invitationId.value
  )

  private def addLinkToInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    invitation.service match {

      case service if service == MtdIt || service == PersonalIncomeRecord =>
        agentLinkService.getInvitationUrl(invitation.arn, "personal").map { link =>
          Some(invitation.copy(clientActionUrl = Some(s"$invitationsFrontendBaseUrl$link")))
        }

      case service if service == Vat || service == Trust =>
        agentLinkService.getInvitationUrl(invitation.arn, "business").map { link =>
          Some(invitation.copy(clientActionUrl = Some(s"$invitationsFrontendBaseUrl$link")))
        }

      case _ => Future.successful(None)
    }

  private def toListOfServices(servicesOpt: Option[String]) =
    servicesOpt match {
      case Some(string) => string.replace(" ", "").split(",").map(Service(_)).toSeq
      case _            => Seq.empty[Service]
    }

  def getSentInvitations(
    givenArn: Arn,
    clientType: Option[String],
    service: Option[String],
    clientIdType: Option[String],
    clientId: Option[String],
    status: Option[InvitationStatus],
    createdOnOrAfter: Option[LocalDate]): Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    forThisAgency(givenArn) {
      val csvServices = toListOfServices(service)
      val invitationsWithOptLinks: Future[List[Invitation]] = for {
        invitations <- invitationsService
                        .findInvitationsBy(Some(arn), csvServices, clientId, status, createdOnOrAfter)
        invitationsWithLink <- Future.traverse(invitations) { invites =>
                                (invites.clientType, invites.status) match {
                                  case (Some(ct), Pending) =>
                                    agentLinkService.getInvitationUrl(invites.arn, ct).map { link =>
                                      invites.copy(clientActionUrl = Some(s"$invitationsFrontendBaseUrl$link"))
                                    }
                                  case _ => Future.successful(invites)
                                }
                              }
      } yield invitationsWithLink

      invitationsWithOptLinks.map { invitations =>
        Ok(
          toHalResource(
            invitations.filter(_.arn == givenArn),
            arn,
            clientType,
            service,
            clientIdType,
            clientId,
            status))
      }
    }
  }

  def getSentInvitation(givenArn: Arn, invitationId: InvitationId): Action[AnyContent] = onlyForAgents {
    implicit request => implicit arn =>
      forThisAgency(givenArn) {
        val invitationsWithOptLinks: Future[Option[Invitation]] = for {
          invitation <- invitationsService.findInvitation(invitationId)
          invitationWithLink <- invitation match {
                                 case Some(invite) =>
                                   (invite.clientType, invite.status) match {
                                     case (Some(clientType), Pending) =>
                                       agentLinkService.getInvitationUrl(invite.arn, clientType).map { link =>
                                         Some(invite.copy(clientActionUrl = Some(s"$invitationsFrontendBaseUrl$link")))
                                       }
                                     case _ => Future.successful(Some(invite))
                                   }
                                 case _ => Future successful None
                               }
        } yield invitationWithLink

        invitationsWithOptLinks.map {
          _.map(invitation => if (invitation.arn == givenArn) Ok(toHalResource(invitation)) else Forbidden) getOrElse InvitationNotFound
        }
      }
  }

  private def forThisAgency(requestedArn: Arn)(block: => Future[Result])(implicit arn: Arn) =
    if (requestedArn != arn)
      Future successful NoPermissionOnAgency
    else block

  def checkKnownFactItsa(nino: Nino, postcode: String): Action[AnyContent] = onlyForAgents {
    implicit request => implicit arn =>
      postcodeService.postCodeMatches(nino.value, postcode.replaceAll("\\s", "")).map {
        case Some(error) => error
        case None        => NoContent
      }
  }

  def checkKnownFactVat(vrn: Vrn, vatRegistrationDate: LocalDate): Action[AnyContent] = onlyForAgents {
    implicit request => implicit arn =>
      knownFactsCheckService
        .clientVatRegistrationDateMatches(vrn, vatRegistrationDate)
        .map {
          case Some(true)  => NoContent
          case Some(false) => VatRegistrationDateDoesNotMatch
          case None        => NotFound
        }
        .recover {
          case e if e.getMessage.contains("MIGRATION") => {
            Logger(getClass).warn(s"Issues with Check Known Fact for VAT: ${e.getMessage}")
            Locked
          }
          case e =>
            Logger(getClass).warn(s"Error found for Check Known Fact for VAT: ${e.getMessage}")
            BadGateway
        }
  }

  def checkKnownFactIrv(nino: Nino, dateOfBirth: LocalDate): Action[AnyContent] = onlyForAgents {
    implicit request => implicit arn =>
      knownFactsCheckService.clientDateOfBirthMatches(nino, dateOfBirth).map {
        case Some(true)  => NoContent
        case Some(false) => DateOfBirthDoesNotMatch
        case None        => NotFound
      }
  }

  def getTrustName(utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      trustCache(utr.value) {
        desConnector.getTrustName(utr)
      }.map(r => Ok(Json.toJson(r)))
    }
  }

  def getCgtSubscriptionDetails(cgtRef: CgtRef): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      cgtCache(cgtRef.value) {
        desConnector.getCgtSubscription(cgtRef)
      }.map { cgtSubscription =>
        cgtSubscription.response match {
          case Right(sub) => Ok(Json.toJson(sub))
          case Left(cgtError) =>
            cgtError.httpResponseCode match {
              case 400 => BadRequest(Json.toJson(cgtError.errors))
              case 404 => NotFound(Json.toJson(cgtError.errors))
            }
        }
      }
    }
  }
}
