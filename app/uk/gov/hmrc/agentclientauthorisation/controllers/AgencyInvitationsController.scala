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

import cats.data.OptionT
import com.kenshoo.play.metrics.Metrics
import org.mongodb.scala.MongoException
import play.api.http.HeaderNames
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, CitizenDetailsConnector, DesConnector, EisConnector, IfConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AgentInvitationValidation
import uk.gov.hmrc.agentclientauthorisation.model.Pillar2KnownFactCheckResult.{Pillar2DetailsNotFound, Pillar2KnownFactCheckOk, Pillar2KnownFactNotMatched, Pillar2RecordClientInactive}
import uk.gov.hmrc.agentclientauthorisation.model.VatKnownFactCheckResult.{VatDetailsNotFound, VatKnownFactCheckOk, VatKnownFactNotMatched, VatRecordClientInsolvent}
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted => IAccepted, _}
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentclientauthorisation.util.{FailedResultException, valueOps}
import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDate
import javax.inject._
import scala.concurrent.duration._
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
  ifConnector: IfConnector,
  eisConnector: EisConnector,
  authConnector: AuthConnector,
  citizenDetailsConnector: CitizenDetailsConnector,
  agentCacheProvider: AgentCacheProvider)(
  implicit
  metrics: Metrics,
  cc: ControllerComponents,
  futures: Futures,
  val ec: ExecutionContext)
    extends AuthActions(metrics, appConfig, authConnector, cc) with HalWriter with AgentInvitationValidation with AgencyInvitationsHal {

  private val trustCache = agentCacheProvider.trustResponseCache
  private val cgtCache = agentCacheProvider.cgtSubscriptionCache
  private val pptCache = agentCacheProvider.pptSubscriptionCache

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

  def replaceUrnInvitationWithUtr(givenUrn: Urn, utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    invitationsService.findLatestInvitationByClientId(clientId = givenUrn.value) flatMap {
      case Some(existingInvite) if existingInvite.status == Pending => {
        for {
          _ <- invitationsService.create(existingInvite.arn, existingInvite.clientType, Trust, utr, utr, existingInvite.origin)
          _ <- invitationsService.cancelInvitation(existingInvite)
        } yield Created
      }.recoverWith {
        case StatusUpdateFailure(_, msg) => Future successful invalidInvitationStatus(msg)
      }
      case Some(i) if i.status == IAccepted && !i.isRelationshipEnded =>
        for {
          _          <- invitationsService.setRelationshipEnded(i, "HMRC")
          invitation <- invitationsService.create(i.arn, i.clientType, Trust, utr, utr, i.origin)
          _          <- futures.delayed(500.millisecond)(invitationsService.acceptInvitationStatus(invitation))
        } yield Created
      case Some(_) => Future successful NoContent
      case _       => Future successful NotFound
    }
  }

  def getInvitationUrl(givenArn: Arn, clientType: String): Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
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
          invitationsService
            .cancelInvitation(i.copy(origin = request.headers.get("Origin")))
            .map(_ => NoContent)
            .recoverWith { case StatusUpdateFailure(_, msg) => Future successful invalidInvitationStatus(msg) }
        case None => Future successful InvitationNotFound
        case _    => Future successful NoPermissionOnAgency
      }
    }
  }

  def setRelationshipEnded(): Action[AnyContent] =
    Action.async { implicit request =>
      request.body.asJson.map(_.validate[SetRelationshipEndedPayload]) match {
        case Some(JsSuccess(payload, _)) =>
          invitationsService
            .findInvitationAndEndRelationship(payload.arn, payload.clientId, toListOfServices(Some(payload.service)), payload.endedBy)
            .map {
              case true  => NoContent
              case false => InvitationNotFound
            }
        case Some(JsError(e)) => Future successful genericBadRequest(e.mkString)
        case None             => Future successful genericBadRequest("No JSON found in request body")
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
        invitationsService.getClientIdForItsa(agentInvitation.clientId, agentInvitation.clientIdType)
      case _ => Future successful Some(suppliedClientId)
    }) flatMap {
      case None =>
        Future successful ClientRegistrationNotFound
      case Some(taxId) =>
        invitationsService
          .create(arn, agentInvitation.clientType, agentInvitation.getService, taxId, suppliedClientId, originHeader)
          .map(invitation => Created.withHeaders(location(invitation): _*))
          .recover {
            case e: MongoException if e.getMessage.contains("E11000 duplicate key error") =>
              duplicateInvitationError
          }
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
        invitationsWithLink <- Future sequence invitations.map { invite =>
                                (invite.clientType, invite.status) match {
                                  case (Some(ct), Pending) =>
                                    agentLinkService.getInvitationUrl(invite.arn, ct).map { link =>
                                      invite.copy(clientActionUrl = Some(s"${appConfig.agentInvitationsFrontendExternalUrl}$link"))
                                    }
                                  case _ => Future successful invite
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
    postcodeService
      .postCodeMatches(nino.value, postcode.replaceAll("\\s", ""))
      .map(_ => NoContent)
      .recoverWith {
        case FailedResultException(r) if r.header.status == BadRequest.header.status     => r.toFuture
        case FailedResultException(r) if r.header.status == NotImplemented.header.status => r.toFuture
        case FailedResultException(PostcodeDoesNotMatch)                                 => PostcodeDoesNotMatch.toFuture
        case FailedResultException(r) =>
          if (appConfig.altItsaEnabled) checkPostcodeAgainstCitizenDetails(nino, postcode)
          else r.toFuture
      }
      .recover {
        case uer: UpstreamErrorResponse =>
          logger.warn(uer.getMessage(), uer)
          PostcodeDoesNotMatch
      }
  }

  def checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(implicit hc: HeaderCarrier): Future[Result] = {
    for {
      citizen <- OptionT(citizenDetailsConnector.getCitizenDetails(nino))
      _       <- OptionT(citizen.sautr.toFuture)
      details <- OptionT(citizenDetailsConnector.getDesignatoryDetails(nino))
    } yield
      details.postCode.fold[Result](PostcodeDoesNotMatch) { pc =>
        if (pc.toLowerCase.replaceAll("\\s", "") == postcode.toLowerCase.replaceAll("\\s", "")) NoContent
        else PostcodeDoesNotMatch
      }
  }.value.map(_.getOrElse(ClientRegistrationNotFound))

  def checkKnownFactVat(vrn: Vrn, vatRegistrationDate: LocalDate): Action[AnyContent] = onlyForAgents { implicit request => _ =>
    knownFactsCheckService
      .clientVatRegistrationCheckResult(vrn, vatRegistrationDate)
      .map {
        case VatKnownFactCheckOk      => NoContent
        case VatKnownFactNotMatched   => VatRegistrationDateDoesNotMatch
        case VatRecordClientInsolvent => VatClientInsolvent
        case VatDetailsNotFound       => NotFound
      }
      .recover {
        case e if e.getMessage.contains("MIGRATION") =>
          logger.warn(s"Issues with Check Known Fact for VAT: ${e.getMessage}")
          Locked
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

  def getTrustName(trustTaxIdentifier: String): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      trustCache(trustTaxIdentifier) {
        ifConnector.getTrustName(trustTaxIdentifier)
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

  def getPptSubscriptionDetails(pptRef: PptRef): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      pptCache(pptRef.value) {
        ifConnector.getPptSubscriptionRawJson(pptRef)
      }.map {
        case Some(sub) => Ok(Json.toJson(sub))
        case None      => NotFound
      }
    }
  }

  def getCbcSubscriptionDetails(cbcId: CbcId): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      eisConnector.getCbcSubscription(cbcId).map {
        case Some(sub) => Ok(Json.toJson(sub))
        case None      => NotFound
      }
    }
  }

  def getPillar2SubscriptionDetails(plrId: PlrId): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      ifConnector
        .getPillar2Subscription(plrId)
        .map {
          _.response match {
            case Right(sub) => Ok(Json.toJson(sub))
            case Left(pillar2Error) =>
              val json = Json.toJson(pillar2Error.errors)
              pillar2Error.httpResponseCode match {
                case 400 => BadRequest(json)
                case 404 => NotFound(json)
                case c =>
                  logger.warn(s"unexpected status $c from DES, error: ${pillar2Error.errors}")
                  InternalServerError(json)
              }
          }
        }
    }
  }

  def checkKnownFactPpt(EtmpRegistrationNumberNumber: PptRef, dateOfApplication: LocalDate): Action[AnyContent] = Action.async { implicit request =>
    ifConnector
      .getPptSubscription(EtmpRegistrationNumberNumber)
      .map {
        case Some(record) =>
          if (record.dateOfApplication != dateOfApplication) PptRegistrationDateDoesNotMatch
          else
            record.deregistrationDate.fold(NoContent)(deregistrationDate =>
              if (deregistrationDate.isAfter(LocalDate.now)) NoContent else PptCustomerDeregistered)
        case None => PptSubscriptionNotFound
      }
      .recover {
        case e: UpstreamErrorResponse =>
          logger.warn(s"unexpected error on checkKnownFactPpt $e")
          InternalServerError
      }
  }

  def checkKnownFactCbc(cbcId: CbcId): Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.flatMap(json => (json \ "email").asOpt[String]) match {
      case Some(email) =>
        eisConnector.getCbcSubscription(cbcId).map {
          case Some(subscription) =>
            val knownFactOk = subscription.emails.exists(_.trim.equalsIgnoreCase(email.trim))
            if (knownFactOk) { NoContent } else {
              logger.warn(s"checkKnownFactCbc: email does not match CBC subscription for $cbcId")
              Forbidden
            }
          case None =>
            logger.warn(s"checkKnownFactCbc: CBC subscription not found for $cbcId")
            NotFound
        }
      case None => Future.successful(BadRequest)
    }
  }

  def checkKnownFactPillar2(plrId: PlrId): Action[AnyContent] = Action.async { implicit request =>
    request.body.asJson.flatMap(json => (json \ "registrationDate").asOpt[LocalDate]) match {
      case Some(registrationDate) =>
        knownFactsCheckService
          .clientPillar2RegistrationCheckResult(plrId, registrationDate)
          .map {
            case Pillar2KnownFactCheckOk     => NoContent
            case Pillar2KnownFactNotMatched  => Pillar2RegistrationDateDoesNotMatch
            case Pillar2RecordClientInactive => Pillar2ClientInactive
            case Pillar2DetailsNotFound      => NotFound
          }
          .recover {
            case e if e.getMessage.contains("MIGRATION") =>
              logger.warn(s"Issues with Check Known Fact for Pillar2: ${e.getMessage}")
              Locked
            case e =>
              logger.warn(s"Error found for Check Known Fact for Pillar2: ${e.getMessage}")
              BadGateway
          }
      case None => Future.successful(BadRequest)
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
          case e =>
            logger.warn(s"Something has gone for ${arn.value} due to: ${e.getMessage}")
            genericInternalServerError(e.getMessage)
        }
      } else Future successful genericBadRequest(s"Invalid Arn given by Stride user: ${arn.value}")
    }
  }

  def altItsaUpdate(nino: Nino): Action[AnyContent] = Action.async { implicit request =>
    invitationsService
      .updateAltItsaFor(nino)
      .map { result =>
        if (result.nonEmpty) Created else NoContent
      }
      .recover {
        case e =>
          logger.warn(s"alt-itsa update error for ${nino.value} due to: ${e.getMessage}")
          genericInternalServerError(e.getMessage)
      }
  }

  def altItsaUpdateAgent(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    invitationsService
      .updateAltItsaFor(arn)
      .map(_ => NoContent)
      .recover {
        case e =>
          logger.warn(s"alt-itsa error during update for agent ${arn.value} due to: ${e.getMessage}")
          genericInternalServerError(e.getMessage)
      }
  }
}
