/*
 * Copyright 2021 HM Revenue & Customs
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
import play.api.http.HeaderNames
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, DesConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{ClientRegistrationNotFound, DateOfBirthDoesNotMatch, InvitationNotFound, NoPermissionOnAgency, VatRegistrationDateDoesNotMatch, genericBadRequest, genericInternalServerError, invalidInvitationStatus}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AgentInvitationValidation
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class AgencyInvitationsController @Inject()(
  appConfig: AppConfig,
  postcodeService: PostcodeService,
  invitationsService: InvitationsService,
  knownFactsCheckService: KnownFactsCheckService,
  agentLinkService: AgentLinkService,
  desConnector: DesConnector,
  authConnector: AuthConnector,
  agentCacheProvider: AgentCacheProvider)(
  implicit
  metrics: Metrics,
  cc: ControllerComponents,
  val ec: ExecutionContext)
    extends AuthActions(metrics, authConnector, cc) with HalWriter with AgentInvitationValidation with AgencyInvitationsHal {

  private val trustCache = agentCacheProvider.trustResponseCache
  private val cgtCache = agentCacheProvider.cgtSubscriptionCache

  def createInvitation(givenArn: Arn): Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    forThisAgency(givenArn) {
      val invitationJson: Option[JsValue] = request.body.asJson
      localWithJsonBody(
        { agentInvitation =>
          implicit val originHeader: Option[String] = request.headers.get("Origin")
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

  def cancelInvitation(givenArn: Arn, invitationId: InvitationId): Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    forThisAgency(givenArn) {
      invitationsService.findInvitation(invitationId) flatMap {
        case Some(i) if i.arn == givenArn =>
          invitationsService.cancelInvitation(i.copy(origin = request.headers.get("Origin"))) map {
            case Right(_)                          => NoContent
            case Left(StatusUpdateFailure(_, msg)) => invalidInvitationStatus(msg)
          }
        case None => Future successful InvitationNotFound
        case _    => Future successful NoPermissionOnAgency
      }
    }
  }

  def setRelationshipEnded(invitationId: InvitationId, endedBy: String): Action[AnyContent] =
    Action.async { implicit request =>
      withBasicAuth {
        invitationsService.findInvitation(invitationId) flatMap {
          case Some(i) =>
            invitationsService.setRelationshipEnded(i, endedBy) map { _ =>
              NoContent
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

  private def makeInvitation(arn: Arn, agentInvitation: AgentInvitation)(implicit originHeader: Option[String], hc: HeaderCarrier): Future[Result] = {
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
          .create(arn, agentInvitation.clientType, agentInvitation.getService, taxId, suppliedClientId, originHeader)
          .map(invitation => Created.withHeaders(location(invitation): _*))
    }
  }

  private def location(invitation: Invitation) = Seq(
    LOCATION       -> routes.AgencyInvitationsController.getSentInvitation(invitation.arn, invitation.invitationId).url,
    "InvitationId" -> invitation.invitationId.value
  )

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
        normalisedAgentName <- agentLinkService.agencyName(arn)
        record              <- agentLinkService.fetchOrCreateRecord(arn, normalisedAgentName)
        invitationsWithLink <- Future successful invitations.map { invite =>
                                (invite.clientType, invite.status) match {
                                  case (Some(ct), Pending) =>
                                    val link = s"/invitations/$ct/${record.uid}/$normalisedAgentName"
                                    invite.copy(clientActionUrl = Some(s"${appConfig.agentInvitationsFrontendExternalUrl}$link"))

                                  case _ => invite
                                }
                              }
      } yield invitationsWithLink

      invitationsWithOptLinks.map { invitations =>
        Ok(toHalResource(invitations.filter(_.arn == givenArn), arn, clientType, service, clientIdType, clientId, status))
      }
    }
  }

  def getSentInvitation(givenArn: Arn, invitationId: InvitationId): Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    forThisAgency(givenArn) {
      val invitationsWithOptLinks: Future[Option[Invitation]] = for {
        invitation <- invitationsService.findInvitation(invitationId)
        invitationWithLink <- invitation match {
                               case Some(invite) =>
                                 (invite.clientType, invite.status) match {
                                   case (Some(clientType), Pending) =>
                                     agentLinkService.getInvitationUrl(invite.arn, clientType).map { link =>
                                       Some(invite.copy(clientActionUrl = Some(s"${appConfig.agentInvitationsFrontendExternalUrl}$link")))
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

  def checkKnownFactItsa(nino: Nino, postcode: String): Action[AnyContent] = onlyForAgents { implicit request => _ =>
    postcodeService.postCodeMatches(nino.value, postcode.replaceAll("\\s", "")).map {
      case Some(error) => error
      case None        => NoContent
    }
  }

  def checkKnownFactVat(vrn: Vrn, vatRegistrationDate: LocalDate): Action[AnyContent] = onlyForAgents { implicit request => _ =>
    knownFactsCheckService
      .clientVatRegistrationDateMatches(vrn, vatRegistrationDate)
      .map {
        case Some(true)  => NoContent
        case Some(false) => VatRegistrationDateDoesNotMatch
        case None        => NotFound
      }
      .recover {
        case e if e.getMessage.contains("MIGRATION") => {
          logger.warn(s"Issues with Check Known Fact for VAT: ${e.getMessage}")
          Locked
        }
        case e =>
          logger.warn(s"Error found for Check Known Fact for VAT: ${e.getMessage}")
          BadGateway
      }
  }

  def checkKnownFactIrv(nino: Nino, dateOfBirth: LocalDate): Action[AnyContent] = onlyForAgents { implicit request => _ =>
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
            val json = Json.toJson(cgtError.errors)
            cgtError.httpResponseCode match {
              case 400 => BadRequest(json)
              case 404 => NotFound(json)
              case c =>
                logger.warn(s"unexpected status $c from DES, error: ${cgtError.errors}")
                InternalServerError(json)
            }
        }
      }
    }
  }

  def removeAllInvitationsAndReferenceForArn(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth(appConfig.expectedAuth) {
      if (Arn.isValid(arn.value)) {
        (for {
          invitationsDeleted <- invitationsService.removeAllInvitationsForAgent(arn)
          referencesDeleted  <- agentLinkService.removeAgentReferencesForGiven(arn)
        } yield {
          Ok(
            Json.toJson[TerminationResponse](
              TerminationResponse(
                Seq(
                  DeletionCount(appConfig.appName, "invitations", invitationsDeleted),
                  DeletionCount(appConfig.appName, "agent-reference", referencesDeleted)
                ))))
        }).recover {
          case e => {
            logger.warn(s"Something has gone for ${arn.value} due to: ${e.getMessage}")
            genericInternalServerError(e.getMessage)
          }
        }
      } else Future successful genericBadRequest(s"Invalid Arn given by Stride user: ${arn.value}")
    }
  }
}
