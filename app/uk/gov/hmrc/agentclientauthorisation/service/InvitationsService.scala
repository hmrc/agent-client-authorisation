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

package uk.gov.hmrc.agentclientauthorisation.service

import akka.Done
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service.MtdIt
import uk.gov.hmrc.agentclientauthorisation.model.{InvitationStatus, _}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, Monitor}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, _}
import scala.collection.Seq
import scala.concurrent.Future.successful

case class StatusUpdateFailure(currentStatus: InvitationStatus, failureReason: String) extends Throwable

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject()(
  invitationsRepository: InvitationsRepository,
  relationshipsConnector: RelationshipsConnector,
  analyticsService: PlatformAnalyticsService,
  desConnector: DesConnector,
  emailService: EmailService,
  appConfig: AppConfig,
  metrics: Metrics)
    extends Monitor with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val invitationExpiryDuration = appConfig.invitationExpiringDuration

  def getClientIdForItsa(clientId: String, clientIdType: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[ClientId]] =
    clientIdType match {
      case MtdItIdType.id => Future successful Some(MtdItId(clientId))
      case NinoType.id =>
        desConnector.getBusinessDetails(Nino(clientId)).map {
          case Some(record) => record.mtdbsa.map(ClientIdentifier(_))
          case None         => if (Nino.isValid(clientId)) Some(ClientIdentifier(clientId, clientIdType)) else None
        }
      case _ => Future successful None
    }

  def create(arn: Arn, clientType: Option[String], service: Service, clientId: ClientId, suppliedClientId: ClientId, originHeader: Option[String])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Invitation] = {
    val startDate = currentTime()
    val expiryDate = startDate.plus(invitationExpiryDuration.toMillis).toLocalDate
    monitor(s"Repository-Create-Invitation-${service.id}") {
      for {
        detailsForEmail <- emailService.createDetailsForEmail(arn, clientId, service)
        invitation <- invitationsRepository
                       .create(arn, clientType, service, clientId, suppliedClientId, Some(detailsForEmail), startDate, expiryDate, originHeader)
        _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation)
      } yield {
        logger.info(s"""Created invitation with id: "${invitation.id.stringify}".""")
        invitation
      }
    }
  }

  def acceptInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Invitation] = {
    val acceptedDate = currentTime()
    invitation.status match {
      case Pending =>
        for {
          // accept the invite
          _          <- createRelationship(invitation, acceptedDate)
          invitation <- changeInvitationStatus(invitation, model.Accepted, acceptedDate)

          // mark any existing relationships as de-authed, this is not critical, so on failure, just move on
          activeRelationships <- relationshipsConnector.getActiveRelationships.map(_.get(invitation.service.id)).fallbackTo(successful(None))
          _                   <- updateInvitationStatuses(activeRelationships, invitation).fallbackTo(successful(Nil))

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

  private def updateInvitationStatuses(activeRelationships: Option[Seq[Arn]], invitation: Invitation)(implicit ec: ExecutionContext) =
    Future
      .sequence(
        activeRelationships.getOrElse(Nil).map { arn =>
          invitationsRepository.findInvitationsBy(Some(arn), Seq(invitation.service)).flatMap { invitations =>
            Future.sequence(
              onlyOldInvitations(invitation, invitations).collect {
                case invite if invite.status == Accepted =>
                  changeInvitationStatus(invite, DeAuthorised, currentTime())
              }
            )
          }
        }
      )
      .map(_.flatten)

  private def onlyOldInvitations(invitation: Invitation, invitations: Seq[Invitation]): Seq[Invitation] =
    invitations.filterNot(i => i.firstEvent() == invitation.firstEvent())

  def acceptInvitationStatus(invitation: Invitation)(implicit ec: ExecutionContext): Future[Invitation] = {
    val acceptedDate = currentTime()
    changeInvitationStatus(invitation, model.Accepted, acceptedDate)
  }

  private def createRelationship(invitation: Invitation, acceptedDate: DateTime)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val createRelationship: Future[Unit] = invitation.service match {
      case Service.MtdIt                => relationshipsConnector.createMtdItRelationship(invitation)
      case Service.PersonalIncomeRecord => relationshipsConnector.createAfiRelationship(invitation, acceptedDate)
      case Service.Vat                  => relationshipsConnector.createMtdVatRelationship(invitation)
      case Service.Trust                => relationshipsConnector.createTrustRelationship(invitation)
      case Service.TrustNT              => relationshipsConnector.createTrustRelationship(invitation)
      case Service.CapitalGains         => relationshipsConnector.createCapitalGainsRelationship(invitation)
    }

    createRelationship.recover {
      case e if e.getMessage.contains("RELATIONSHIP_ALREADY_EXISTS") =>
        logger.warn(s"Error Found: ${e.getMessage} \n Client has accepted an invitation despite previously delegated the same agent")
    }
  }

  def findInvitationsInfoBy(arn: Arn, clientIds: Seq[(String, String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]] =
    invitationsRepository.findInvitationInfoBy(arn, clientIds, status)

  def cancelInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Invitation] =
    for {
      invitation <- changeInvitationStatus(invitation, model.Cancelled)
      _ = reportHistogramValue("Duration-Invitation-Cancelled", durationOf(invitation))
      _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation).fallbackTo(successful(Done))
    } yield invitation

  def setRelationshipEnded(invitation: Invitation, endedBy: String)(implicit ec: ExecutionContext): Future[Invitation] =
    monitor(s"Repository-Change-Invitation-${invitation.service.id}-flagRelationshipEnded") {
      for {
        _                 <- changeInvitationStatus(invitation, model.DeAuthorised)
        updatedInvitation <- invitationsRepository.setRelationshipEnded(invitation, endedBy)
      } yield {
        logger info s"""Invitation with id: "${invitation.id.stringify}" has been flagged as isRelationshipEnded = true"""
        updatedInvitation
      }
    }

  def rejectInvitation(invitation: Invitation)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Invitation] =
    for {
      invitation <- changeInvitationStatus(invitation, model.Rejected).fallbackTo(successful(invitation))
      _ = reportHistogramValue("Duration-Invitation-Rejected", durationOf(invitation))
      _ <- emailService.sendRejectedEmail(invitation).fallbackTo(successful(()))
      _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation).fallbackTo(successful(Done))
    } yield invitation

  def findInvitation(invitationId: InvitationId)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    monitor(s"Repository-Find-Invitation-${invitationId.value.charAt(0)}") {
      invitationsRepository.findByInvitationId(invitationId)
    }

  def findLatestInvitationByClientId(clientId: String)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    monitor(s"Repository-Find-Latest-Invitation-$clientId") {
      invitationsRepository.findLatestInvitationByClientId(clientId)
    }

  def clientsReceived(service: Service, clientId: ClientId, status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[Seq[Invitation]] =
    monitor(s"Repository-List-Invitations-Received-$service${status.map(s => s"-$s").getOrElse("")}") {
      invitationsRepository.findInvitationsBy(services = Seq(service), clientId = Some(clientId.value), status = status)
    }

  def findInvitationsBy(
    arn: Option[Arn] = None,
    services: Seq[Service] = Seq.empty[Service],
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None)(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val csvServices: Seq[String] = if (services.nonEmpty) services.map(s => s"-${s.id}") else Seq("")
    monitor(s"Repository-List-Invitations-Sent$csvServices") {
      invitationsRepository.findInvitationsBy(arn, services, clientId, status, createdOnOrAfter)
    }
  }

  def findInvitationsInfoBy(
    arn: Option[Arn] = None,
    service: Option[Service] = None,
    clientId: Option[String] = None,
    status: Option[InvitationStatus] = None,
    createdOnOrAfter: Option[LocalDate] = None)(implicit ec: ExecutionContext): Future[List[InvitationInfo]] =
    monitor(s"Repository-List-InvitationInfo${service
      .map(s => s"-${s.id}")
      .getOrElse("")}${status.map(s => s"-$s").getOrElse("")}") {
      invitationsRepository.findInvitationInfoBy(arn, service, clientId, status, createdOnOrAfter)
    }

  def getNonSuspendedInvitations(
    identifiers: Seq[(Service, String)])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[List[InvitationInfo]]] = {

    def isArnSuspendedForService(arn: Arn, service: Service): Future[Option[Boolean]] =
      desConnector
        .getAgencyDetails(arn)
        .map(details =>
          details.map(_.suspensionDetails.getOrElse(SuspensionDetails.notSuspended)).map { sd =>
            sd.isRegimeSuspended(service)
        })

    for {
      invitations <- Future.sequence(identifiers.map {
                      case (service, clientId) =>
                        findInvitationsInfoBy(service = Some(service), clientId = Some(clientId))
                    })
      invs <- Future.sequence(
               invitations.flatMap(invs => invs.map(inv => isArnSuspendedForService(inv.arn, inv.service).map(isSuspended => (invs, isSuspended)))))
    } yield invs.filter(!_._2.getOrElse(false)).map(_._1)
  }

  def findAndUpdateExpiredInvitations()(implicit ec: ExecutionContext): Future[Unit] =
    monitor(s"Repository-Find-And-Update-Expired-Invitations") {
      invitationsRepository
        .findInvitationsBy(status = Some(Pending))
        .map(invs => invs.filter(_.expiryDate.isBefore(LocalDate.now())))
        .flatMap { invitations =>
          val result = invitations.map(updateToExpiredAndSendEmail)
          Future.sequence(result).map(_ => ())
        }
    }

  private def updateToExpiredAndSendEmail(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit] =
    invitationsRepository
      .update(invitation, Expired, DateTime.now())
      .flatMap(invitation => {
        logger.info(s"invitation expired id:${invitation.invitationId.value}")
        emailService.sendExpiredEmail(invitation)
      })

  private def changeInvitationStatus(invitation: Invitation, status: InvitationStatus, timestamp: DateTime = currentTime())(
    implicit ec: ExecutionContext): Future[Invitation] =
    if (invitation.status == Pending || (invitation.status == Accepted && status == DeAuthorised)) {
      monitor(s"Repository-Change-Invitation-${invitation.service.id}-Status-From-${invitation.status}-To-$status") {
        invitationsRepository.update(invitation, status, timestamp) map { invitation =>
          logger info s"""Invitation with id: "${invitation.id.stringify}" has been $status"""
          invitation
        }
      }
    } else Future failed invalidTransition(invitation, status)

  private def invalidTransition(invitation: Invitation, toStatus: InvitationStatus) =
    StatusUpdateFailure(
      invitation.status,
      s"The invitation cannot be transitioned to $toStatus because its current status is ${invitation.status}."
    )

  private def currentTime() = DateTime.now(DateTimeZone.UTC)

  private def durationOf(invitation: Invitation): Long =
    if (invitation.events.isEmpty) 0
    else
      System.currentTimeMillis() - invitation.firstEvent().time.getMillis

  def removePersonalDetails()(implicit ec: ExecutionContext): Future[Unit] = {
    val infoRemovalDate = DateTime.now().minusSeconds(appConfig.removePersonalInfoExpiryDuration.toSeconds.toInt)
    invitationsRepository.removePersonalDetails(infoRemovalDate)
  }

  def removeAllInvitationsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int] =
    invitationsRepository.removeAllInvitationsForAgent(arn)

  def prepareAndSendWarningEmail()(implicit ec: ExecutionContext): Future[Unit] =
    monitor("Repository-Find-invitations-about-to-expire") {
      invitationsRepository
        .findInvitationsBy(status = Some(Pending))
        .map(
          _.filter(_.expiryDate.isEqual(LocalDate.now().plusDays(appConfig.sendEmailPriorToExpireDays)))
        )
        .map {
          _.groupBy(_.arn).map {
            case (_, list) =>
              emailService.sendWarningToExpire(list)
          }
        }
        .map(_ => ())
    }

  def updateAltItsaFor(taxIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[List[Invitation]] =
    fetchAltItsaInvitationsFor(taxIdentifier)
      .flatMap(updateInvitationStoreIfMtdItIdExists(_))
      .flatMap(Future sequence _.filter(i => i.status == PartialAuth).map(createRelationshipAndUpdateStatus(_)))

  private def updateInvitationStoreIfMtdItIdExists(invitations: List[Invitation])(implicit hc: HeaderCarrier, ec: ExecutionContext) =
    Future sequence invitations.map { inv =>
      desConnector
        .getMtdIdFor(Nino(inv.suppliedClientId.value))
        .flatMap {
          case Some(mtdId) => invitationsRepository.replaceNinoWithMtdItIdFor(inv, mtdId)
          case None        => Future successful inv
        }
    }

  private def createRelationshipAndUpdateStatus(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext) = {
    val timeNow = DateTime.now(DateTimeZone.UTC)
    createRelationship(invitation, timeNow)
      .flatMap(_ => invitationsRepository.update(invitation, Accepted, timeNow))
  }

  private def fetchAltItsaInvitationsFor(taxIdentifier: TaxIdentifier)(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val (nino, arn) = taxIdentifier match {
      case n: Nino => (Some(n), None)
      case a: Arn  => (None, Some(a))
      case e       => throw new Exception(s"unexpected TaxIdentifier $e for fetch alt-itsa")
    }
    monitor("Repository-Find-Alt-ITSA-invitations") {
      invitationsRepository
        .findInvitationsBy(arn = arn, clientId = nino.map(n => n.nino), services = List(MtdIt))
        .map(_.filter(i => (i.clientId == i.suppliedClientId) || i.status == PartialAuth))
    }
  }
}
