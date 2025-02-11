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

package uk.gov.hmrc.agentclientauthorisation.service

import org.apache.pekko.Done
import play.api.Logging
import play.api.http.Status.{NOT_FOUND, NO_CONTENT}
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, IfConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.AltItsaUpdateResult._
import uk.gov.hmrc.agentclientauthorisation.model.Invitation.toInvitationInfo
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.util.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service.{MtdIt, MtdItSupp}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import javax.inject._
import scala.concurrent.Future.successful
import scala.util.{Failure, Success}

case class StatusUpdateFailure(currentStatus: InvitationStatus, failureReason: String) extends Throwable

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject() (
  invitationsRepository: InvitationsRepository,
  relationshipsConnector: RelationshipsConnector,
  analyticsService: PlatformAnalyticsService,
  desConnector: DesConnector,
  ifConnector: IfConnector,
  emailService: EmailService,
  auditService: AuditService,
  appConfig: AppConfig,
  val metrics: Metrics
)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor with Logging {

  def getClientIdForItsa(clientId: String, clientIdType: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[ClientId]] =
    clientIdType match {
      case MtdItIdType.id => Future successful Some(MtdItId(clientId))
      case NinoType.id =>
        ifConnector.getBusinessDetails(Nino(clientId)).map {
          case Some(record) => record.mtdId.map(ClientIdentifier(_))
          case None         => if (appConfig.altItsaEnabled && Nino.isValid(clientId)) Some(ClientIdentifier(clientId, clientIdType)) else None
        }
      case _ => Future successful None
    }

  def create(arn: Arn, clientType: Option[String], service: Service, clientId: ClientId, suppliedClientId: ClientId, originHeader: Option[String])(
    implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Invitation] =
    if (appConfig.acrMongoActivated) {
      createAcrInvitation(arn, clientType, service, clientId, suppliedClientId)
    } else {
      createAcaInvitation(arn, clientType, service, clientId, suppliedClientId, originHeader)
    }

  // This is the 'old' way to create invitations in ACA instead of ACR
  private def createAcaInvitation(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    originHeader: Option[String]
  )(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Invitation] =
    for {
      detailsForEmail <- emailService.createDetailsForEmail(arn, clientId, service)
      invitation <- invitationsRepository
                      .create(arn, clientType, service, clientId, suppliedClientId, Some(detailsForEmail), originHeader)
      _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation)
    } yield {
      logger.info(s"""Created invitation with ACA id: "${invitation._id.toString}".""")
      invitation
    }

  // This is the transitional way we're creating invitations in ACR instead of ACA
  private def createAcrInvitation(arn: Arn, clientType: Option[String], service: Service, clientId: ClientId, suppliedClientId: ClientId)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Invitation] =
    for {
      detailsForEmail <- emailService.createDetailsForEmail(arn, clientId, service)
      authorisationRequest = AuthorisationRequest(clientId.value, suppliedClientId.typeId, detailsForEmail.clientName, service.id, clientType)
      acrInvitationId <- relationshipsConnector.sendAuthorisationRequest(arn.value, authorisationRequest)
      startDate = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime
      expiryDate = startDate.plusSeconds(appConfig.invitationExpiringDuration.toSeconds).toLocalDate
      acaInvitationModel = Invitation
                             .createNew(arn, clientType, service, clientId, suppliedClientId, Some(detailsForEmail), startDate, expiryDate, None)
                             .copy(invitationId = InvitationId(acrInvitationId))
    } yield {
      logger.info(s"""Created invitation with ACR id: "${acaInvitationModel.invitationId.value}".""")
      acaInvitationModel
    }

  def acceptInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Invitation] = {
    val acceptedDate = currentTime()
    val isAltItsa = Seq(MtdIt, MtdItSupp).contains(invitation.service) && invitation.clientId == invitation.suppliedClientId
    val nextStatus = if (isAltItsa) PartialAuth else Accepted
    invitation.status match {
      case Pending =>
        for {
          // create new relationship
          _ <- deauthExistingRelationshipForItsaForTheSameAgent(invitation)
          _ <- createRelationship(invitation, acceptedDate)

          invitation <- changeInvitationStatus(invitation, nextStatus, acceptedDate)

          // mark any existing relationships as de-authed, this is not critical, so on failure, just move on
          existingInvitations <- getExistingInvitations(invitation)

          _ <- deauthExistingInvitations(existingInvitations, invitation).fallbackTo(successful(Nil))

          // audit, don't fail on these
          _ <- emailService.sendAcceptedEmail(invitation).fallbackTo(successful(()))
          _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation).fallbackTo(successful(Done))
        } yield {
          reportHistogramValue("Duration-Invitation-Accepted", durationOf(invitation))
          invitation
        }
      case _ => Future failed invalidTransition(invitation, Accepted)
    }
  }

  private def getExistingInvitations(invitation: Invitation)(implicit hc: HeaderCarrier): Future[List[Invitation]] = {
    val itsaInvitationStatus = if (invitation.clientId == invitation.suppliedClientId) PartialAuth else Accepted
    invitation.service match {
      case Service.MtdItSupp =>
        // get MtdItSupp or MtdIt main invitations for the same agent only
        findInvitationsBy(
          arn = Some(invitation.arn),
          services = Seq(Service.MtdIt, Service.MtdItSupp),
          clientId = Some(invitation.clientId.value),
          status = Some(itsaInvitationStatus)
        )
          .fallbackTo(successful(Nil))

      case Service.MtdIt =>
        for {
          // get MtdItSupp invitations for the same agent
          mtdItSuppInvitationForSameAgent <- findInvitationsBy(
                                               arn = Some(invitation.arn),
                                               services = Seq(Service.MtdItSupp),
                                               clientId = Some(invitation.clientId.value),
                                               status = Some(itsaInvitationStatus)
                                             )
          // get all MtdIt main invitations for all agents
          mtdItMainInvitationsForAllAgents <- findInvitationsBy(
                                                services = Seq(Service.MtdIt),
                                                clientId = Some(invitation.clientId.value),
                                                status = Some(itsaInvitationStatus)
                                              )
        } yield (Nil ++ mtdItSuppInvitationForSameAgent ++ mtdItMainInvitationsForAllAgents)

      case _ =>
        // get all invitations for service for all agents
        findInvitationsBy(
          services = Seq(invitation.service),
          clientId = Some(invitation.clientId.value),
          status = Some(Accepted)
        )
          .fallbackTo(successful(Nil))
    }
  }

  private def deauthExistingRelationshipForItsaForTheSameAgent(
    invitation: Invitation
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    if (appConfig.itsaSupportingAgentEnabled) {
      val otherItsaService = if (invitation.service == Service.MtdIt) Service.MtdItSupp else Service.MtdIt
      invitation.service match {
        case Service.MtdIt | Service.MtdItSupp =>
          relationshipsConnector
            .checkItsaRelationship(invitation.arn, otherItsaService, invitation.clientId)
            .flatMap(hasRelationship =>
              if (hasRelationship) {
                // request to delete
                relationshipsConnector
                  .deleteItsaRelationship(invitation.arn, otherItsaService, invitation.clientId)
                  .map { hasDeleted =>
                    if (hasDeleted) ()
                    else
                      logger
                        .warn(
                          s"Error. Delete $otherItsaService service relationship failed. Client has accepted ${invitation.service.id}  invitation."
                        )
                  }
              } else Future.unit
            )
        case _ => Future.unit
      }
    } else Future.unit

  def acceptInvitationStatus(invitation: Invitation)(implicit ec: ExecutionContext, headerCarrier: HeaderCarrier): Future[Invitation] = {
    val acceptedDate = currentTime()
    changeInvitationStatus(invitation, model.Accepted, acceptedDate)
  }

  private def createRelationship(invitation: Invitation, acceptedDate: LocalDateTime)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val createRelationship: Future[Unit] = invitation.service match {
      case Service.MtdIt if invitation.clientId == invitation.suppliedClientId     => Future.successful(())
      case Service.MtdItSupp if invitation.clientId == invitation.suppliedClientId => Future.successful(())
      case Service.MtdIt                                                           => relationshipsConnector.createMtdItRelationship(invitation)
      case Service.MtdItSupp                                                       => relationshipsConnector.createMtdItSuppRelationship(invitation)
      case Service.PersonalIncomeRecord => relationshipsConnector.createAfiRelationship(invitation, acceptedDate)
      case Service.Vat                  => relationshipsConnector.createMtdVatRelationship(invitation)
      case Service.Trust                => relationshipsConnector.createTrustRelationship(invitation)
      case Service.TrustNT              => relationshipsConnector.createTrustRelationship(invitation)
      case Service.CapitalGains         => relationshipsConnector.createCapitalGainsRelationship(invitation)
      case Service.Ppt                  => relationshipsConnector.createPlasticPackagingTaxRelationship(invitation)
      case Service.Cbc                  => relationshipsConnector.createCountryByCountryRelationship(invitation)
      case Service.CbcNonUk             => relationshipsConnector.createCountryByCountryRelationship(invitation)
      case Service.Pillar2              => relationshipsConnector.createPillar2Relationship(invitation)
    }

    createRelationship.recover {
      case e if e.getMessage.contains("RELATIONSHIP_ALREADY_EXISTS") =>
        logger.warn(s"Error Found: ${e.getMessage} \n Client has accepted an invitation despite previously delegated the same agent")
    }
  }

  def findInvitationsInfoBy(
    arn: Arn,
    clientIds: Seq[(String, String, String)],
    status: Option[InvitationStatus]
  )(implicit hc: HeaderCarrier): Future[List[InvitationInfo]] =
    for {
      acrInvitations <- if (appConfig.acrMongoActivated) {
                          val services = clientIds.map(_._1).distinct.map(Service.forId)
                          val clientIdValues = clientIds.map(_._3)
                          relationshipsConnector.lookupInvitations(Some(arn), services, clientIdValues, status).map(_.map(toInvitationInfo))
                        } else Future.successful(List.empty[InvitationInfo])
      acaInvitations <- invitationsRepository.findInvitationInfoBy(arn, clientIds, status)
    } yield acrInvitations ++ acaInvitations

  def cancelInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Invitation] = {
    val nextStatus = if (invitation.status == PartialAuth) DeAuthorised else Cancelled
    for {
      invitation <- changeInvitationStatus(invitation, nextStatus)
      _ = reportHistogramValue("Duration-Invitation-Cancelled", durationOf(invitation))
      _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation).fallbackTo(successful(Done))
    } yield invitation
  }

  def setRelationshipEnded(invitation: Invitation, endedBy: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    if (invitation.fromAcr)
      relationshipsConnector.setRelationshipEnded(invitation, endedBy)
    else
      invitationsRepository.setRelationshipEnded(invitation, endedBy).map(_ => ())

  def rejectInvitation(invitation: Invitation)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Invitation] =
    for {
      invitation <- changeInvitationStatus(invitation, model.Rejected).fallbackTo(successful(invitation))
      _ = reportHistogramValue("Duration-Invitation-Rejected", durationOf(invitation))
      _ <- emailService.sendRejectedEmail(invitation).fallbackTo(successful(()))
      _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation).fallbackTo(successful(Done))
    } yield invitation

  def findInvitation(invitationId: InvitationId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[Invitation]] =
    if (appConfig.acrMongoActivated) {
      relationshipsConnector.lookupInvitation(invitationId.value).flatMap {
        case Some(invitation) => Future.successful(Some(invitation.copy(fromAcr = true)))
        case None             => invitationsRepository.findByInvitationId(invitationId)
      }
    } else {
      invitationsRepository.findByInvitationId(invitationId)
    }

  def changeInvitationStatus(invitation: Invitation, status: InvitationStatus, timestamp: LocalDateTime = currentTime())(implicit
    ec: ExecutionContext,
    hc: HeaderCarrier
  ): Future[Invitation] =
    if (invitation.status == Pending || invitation.status == PartialAuth) {
      if (appConfig.acrMongoActivated && invitation.fromAcr) {
        for {
          invitation <- relationshipsConnector
                          .changeACRInvitationStatusById(invitation.invitationId.value, InvitationStatusAction.fromInvitationStatus(status))
                          .flatMap { response =>
                            response.status match {
                              case NO_CONTENT =>
                                relationshipsConnector.lookupInvitation(invitation.invitationId.value).flatMap {
                                  case Some(invitation) =>
                                    logger info s"""Invitation with id: "${invitation._id.toString}" has been $status"""
                                    Future.successful(invitation.copy(fromAcr = true))
                                  case None =>
                                    Future.failed(
                                      new Exception(s"Invitation ${invitation.invitationId.value} update to the new status $status has failed")
                                    )
                                }
                              case NOT_FOUND => Future.failed(new Exception(s"Invitation ${invitation.invitationId.value} not found"))
                              case _ =>
                                Future.failed(
                                  new Exception(s"Invitation ${invitation.invitationId.value} update to the new status $status has failed")
                                )
                            }
                          }
          detailsForEmail <- emailService.createDetailsForEmail(invitation.arn, invitation.clientId, invitation.service)
        } yield invitation.copy(detailsForEmail = Some(detailsForEmail))
      } else {
        invitationsRepository.update(invitation, status, timestamp) map { invitation =>
          logger info s"""Invitation with id: "${invitation._id.toString}" has been $status"""
          invitation
        }
      }
    } else Future failed invalidTransition(invitation, status)

  def deauthExistingInvitations(existingInvitations: Seq[Invitation], invitation: Invitation)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Unit] =
    Future
      .sequence(existingInvitations.filterNot(i => i.firstEvent() == invitation.firstEvent()).collect {
        case invite if invite.status == Accepted | invite.status == PartialAuth =>
          setRelationshipEnded(invite, "Client")
      })
      .map(_ => ())

  def findLatestInvitationByClientId(clientId: String, within30Days: Boolean): Future[Option[Invitation]] =
    invitationsRepository.findLatestInvitationByClientId(clientId, within30Days)

  def clientsReceived(services: Seq[Service], clientId: ClientId, status: Option[InvitationStatus])(implicit
    hc: HeaderCarrier
  ): Future[Seq[Invitation]] =
    for {
      acrInvitations <-
        if (appConfig.acrMongoActivated)
          relationshipsConnector.lookupInvitations(None, services, Seq(clientId.value), status)
        else Future.successful(Nil)

      acaInvitations <-
        invitationsRepository.findInvitationsBy(
          arn = None,
          services = services,
          clientId = Some(clientId.value),
          status = status,
          createdOnOrAfter = None,
          within30Days = appConfig.acrMongoActivated
        )

    } yield acrInvitations ++ acaInvitations

  def updateAltItsaForNino(clientId: ClientId, service: Service)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    if (appConfig.altItsaEnabled && clientId.typeId == NinoType.id)
      updateAltItsaFor(Nino(clientId.value), service).map(_ => ())
    else Future successful (())

  def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty[Service],
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None
  )(implicit hc: HeaderCarrier): Future[List[Invitation]] = for {
    acrInvitations <-
      if (appConfig.acrMongoActivated)
        relationshipsConnector.lookupInvitations(arn, services, clientId.toSeq, status)
      else
        Future.successful(List())
    acaInvitations <-
      invitationsRepository.findInvitationsBy(
        arn,
        services,
        clientId,
        status,
        createdOnOrAfter,
        appConfig.acrMongoActivated
      )
  } yield acrInvitations ++ acaInvitations

  private def findInvitationsInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service],
    clientId: Option[String],
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None
  ): Future[List[InvitationInfo]] =
    invitationsRepository.findInvitationInfoBy(arn, service, clientId, status, createdOnOrAfter, appConfig.acrMongoActivated)

  def getNonSuspendedInvitations(
    identifiers: Seq[(Service, String)]
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[List[InvitationInfo]]] = {

    def isArnSuspendedForService(arn: Arn, service: Service): Future[Option[Boolean]] =
      desConnector
        .getAgencyDetails(Right(arn))
        .map(details =>
          details.map(_.suspensionDetails.getOrElse(SuspensionDetails.notSuspended)).map { sd =>
            sd.isRegimeSuspended(service)
          }
        )

    for {
      invitations <- Future.sequence(identifiers.map { case (service, clientId) =>
                       findInvitationsInfoBy(service = Some(service), clientId = Some(clientId))
                         .flatMap { clientInvitations =>
                           if (service == Service.PersonalIncomeRecord)
                             findInvitationsInfoBy(service = Some(Service.MtdIt), clientId = Some(clientId))
                               .map(pAuthInvitations => clientInvitations ::: pAuthInvitations)
                           else Future successful clientInvitations
                         }
                     })
      invs <- Future.sequence(
                invitations.flatMap(invs => invs.map(inv => isArnSuspendedForService(inv.arn, inv.service).map(isSuspended => (invs, isSuspended))))
              )
    } yield invs.filter(!_._2.getOrElse(false)).map(_._1)
  }

  // this is called via scheduled job
  def findAndUpdateExpiredInvitations()(implicit ec: ExecutionContext): Future[Unit] =
    invitationsRepository
      .findInvitationsBy(status = Some(Pending), within30Days = appConfig.acrMongoActivated)
      .map(invs => invs.filter(_.expiryDate.isBefore(Instant.now().atZone(ZoneOffset.UTC).toLocalDate)))
      .flatMap { invitations =>
        val result = invitations.map(updateToExpiredAndSendEmail)
        Future.sequence(result).map(_ => ())
      }

  private def updateToExpiredAndSendEmail(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit] =
    invitationsRepository
      .update(invitation, Expired, currentTime())
      .flatMap { invitation =>
        logger.info(s"invitation expired id:${invitation.invitationId.value}")
        emailService.sendExpiredEmail(invitation)
      }

  private def invalidTransition(invitation: Invitation, toStatus: InvitationStatus) =
    StatusUpdateFailure(
      invitation.status,
      s"The invitation cannot be transitioned to $toStatus because its current status is ${invitation.status}."
    )

  private def currentTime() = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime

  private def durationOf(invitation: Invitation): Long =
    if (invitation.events.isEmpty) 0
    else
      currentTime().toEpochSecond(ZoneOffset.UTC) - invitation.firstEvent().time.toEpochSecond(ZoneOffset.UTC)

  // this is called via scheduled job
  def removePersonalDetails()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("started 'removePersonalDetails' job")
    val infoRemovalDate =
      Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime.minusSeconds(appConfig.removePersonalInfoExpiryDuration.toSeconds.toInt)
    invitationsRepository
      .removePersonalDetails(infoRemovalDate)
      .map(_ => logger.info("finished 'removePersonalDetails' job"))
  }

  def removeAllInvitationsForAgent(arn: Arn): Future[Int] =
    invitationsRepository.removeAllInvitationsForAgent(arn)

  // this is called via scheduled job
  def prepareAndSendWarningEmail()(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("started 'prepareAndSendWarningEmail' job")
    invitationsRepository
      .findInvitationsBy(status = Some(Pending), within30Days = appConfig.acrMongoActivated)
      .map(
        _.filter(_.expiryDate.isEqual(LocalDate.now().plusDays(appConfig.sendEmailPriorToExpireDays)))
      )
      .map {
        _.groupBy(_.arn).map { case (_, list) =>
          emailService.sendWarningToExpire(list)
        }
      }
      .map(_ => logger.info("finished 'prepareAndSendWarningEmail' job"))
  }

  // this is called via scheduled job
  def cancelOldAltItsaInvitations()(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.info("started 'cancelOldAltItsaInvitations' job")

    for {
      partialAuth <- invitationsRepository.findInvitationsBy(status = Some(PartialAuth), within30Days = appConfig.acrMongoActivated)
      expired = partialAuth.filter(_.mostRecentEvent().time.plusDays(appConfig.altItsaExpiryDays).isBefore(currentTime()))
      _ = Future sequence expired.map { invitation =>
            setRelationshipEnded(invitation, "HMRC").transformWith {
              case Success(_) =>
                logger.info(
                  s"invitation ${invitation.invitationId.value} with status ${invitation.status} and " +
                    s"datetime ${invitation.mostRecentEvent().time} has been deauthorised."
                )
                auditService
                  .sendHmrcExpiredAgentServiceAuthorisationAuditEvent(invitation, "success")
                  .fallbackTo(Future.successful(())) // don't fail on audit errors
                  .map(_ => ())

              case Failure(e) =>
                logger.error(s"expired invitation id ${invitation.invitationId.value} update failed: $e")
                auditService
                  .sendHmrcExpiredAgentServiceAuthorisationAuditEvent(invitation, s"failure $e")
                  .fallbackTo(Future.successful(())) // don't fail on audit errors
                  .map[Invitation](_ => throw e)
            }
          }
    } yield logger.info("finished 'cancelOldAltItsaInvitations' job")
  }

  def updateAltItsaFor(taxIdentifier: TaxIdentifier, service: Service)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[AltItsaUpdateResult] =
    fetchAltItsaInvitationsFor(taxIdentifier, service)
      .flatMap {
        case Nil => Future successful NoAltItsaFound
        case altItsaInvitations =>
          Future
            .sequence(
              altItsaInvitations.map(updateInvitationIfMtdItIdExists)
            )
            .flatMap(_.flatten match {
              case Nil => Future successful NoMtdIdFound
              case updated =>
                Future
                  .sequence(
                    updated
                      .filter(_.status == PartialAuth)
                      .map(createRelationshipAndUpdateStatus)
                  )
                  .map(updateResult => if (updateResult.nonEmpty) RelationshipCreated else NoPartialAuthFound)
            })
      }

  def findInvitationAndEndRelationship(arn: Arn, clientId: String, service: Seq[Service], endedBy: Option[String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Boolean] = {
    implicit def dateTimeOrdering: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isAfter _)
    findInvitationsBy(
      arn = Some(arn),
      services = service,
      clientId = Some(clientId)
    ).map(
      _.filter(i => i.status == Accepted || i.status == PartialAuth)
        .sortBy(_.mostRecentEvent().time) // there could be more than 1 because of a previous failure...we want the most recent.
        .headOption
    ).flatMap {
      case Some(i) => setRelationshipEnded(i, endedBy.getOrElse("HMRC")).map(_ => true)
      case None =>
        logger.warn(s"setRelationshipEnded failed as no invitation was found")
        Future successful false
    }
  }

  private def updateInvitationIfMtdItIdExists(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Invitation]] =
    ifConnector
      .getMtdIdFor(Nino(invitation.suppliedClientId.value))
      .flatMap {
        case Some(mtdId) => invitationsRepository.replaceNinoWithMtdItIdFor(invitation, mtdId)
        case None        => Future successful None
      }

  private def createRelationshipAndUpdateStatus(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val timeNow = currentTime()
    createRelationship(invitation, timeNow)
      .flatMap(_ => invitationsRepository.update(invitation, Accepted, timeNow))
  }

  private def fetchAltItsaInvitationsFor(taxIdentifier: TaxIdentifier, service: Service)(implicit
    ec: ExecutionContext
  ): Future[List[Invitation]] = {
    val (nino, arn) = taxIdentifier match {
      case n: Nino => (Some(n), None)
      case a: Arn  => (None, Some(a))
      case e       => throw new Exception(s"unexpected TaxIdentifier $e for fetch alt-itsa")
    }
    invitationsRepository
      .findInvitationsBy(arn = arn, clientId = nino.map(_.nino), services = List(service), within30Days = appConfig.acrMongoActivated)
      .map(_.filter(i => (i.clientId == i.suppliedClientId) || i.status == PartialAuth))
  }

}
