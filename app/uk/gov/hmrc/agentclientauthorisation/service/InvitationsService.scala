/*
 * Copyright 2020 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, _}
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.{InvitationStatus, _}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, Monitor}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.Seq
import scala.util.Success

case class StatusUpdateFailure(currentStatus: InvitationStatus, failureReason: String)

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject()(
  invitationsRepository: InvitationsRepository,
  agentLinkService: AgentLinkService,
  relationshipsConnector: RelationshipsConnector,
  analyticsService: PlatformAnalyticsService,
  desConnector: DesConnector,
  auditService: AuditService,
  emailService: EmailService,
  appConfig: AppConfig,
  metrics: Metrics)
    extends Monitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val invitationExpiryDuration = appConfig.invitationExpiringDuration

  def translateToMtdItId(clientId: String, clientIdType: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[ClientIdentifier[MtdItId]]] =
    clientIdType match {
      case MtdItIdType.id => Future successful Some(MtdItId(clientId))
      case NinoType.id =>
        desConnector.getBusinessDetails(Nino(clientId)).map {
          case Some(record) => record.mtdbsa.map(ClientIdentifier(_))
          case None         => None
        }
      case _ => Future successful None
    }

  def create(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    originHeader: Option[String])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Invitation] = {
    val startDate = currentTime()
    val expiryDate = startDate.plus(invitationExpiryDuration.toMillis).toLocalDate
    monitor(s"Repository-Create-Invitation-${service.id}") {
      for {
        detailsForEmail <- emailService.createDetailsForEmail(arn, clientId, service)
        invitation <- invitationsRepository
                       .create(
                         arn,
                         clientType,
                         service,
                         clientId,
                         suppliedClientId,
                         Some(detailsForEmail),
                         startDate,
                         expiryDate,
                         originHeader)
        _ <- analyticsService.reportSingleEventAnalyticsRequest(invitation)
      } yield {
        Logger info s"""Created invitation with id: "${invitation.id.stringify}"."""
        invitation
      }
    }
  }

  def acceptInvitation(invitation: Invitation)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Either[StatusUpdateFailure, Invitation]] = {
    val acceptedDate = currentTime()
    invitation.status match {
      case Pending =>
        def changeInvitationStatusAndRecover: Future[Either[StatusUpdateFailure, Invitation]] =
          createRelationship(invitation, acceptedDate).flatMap(
            _ =>
              changeInvitationStatus(invitation, model.Accepted, acceptedDate)
                .andThen {
                  case Success(_) => reportHistogramValue("Duration-Invitation-Accepted", durationOf(invitation))
              })

        for {
          result <- changeInvitationStatusAndRecover
          _ <- result match {
                case Right(invite) => {
                  emailService
                    .sendAcceptedEmail(invite)
                    .map(_ => analyticsService.reportSingleEventAnalyticsRequest(invite))
                }
                case Left(_) => Future.successful(())
              }
        } yield {
          result
        }

      case _ => Future successful cannotTransitionBecauseNotPending(invitation, Accepted)
    }
  }

  private def createRelationship(invitation: Invitation, acceptedDate: DateTime)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext) = {
    val createRelationship: Future[Unit] = invitation.service match {
      case Service.MtdIt                => relationshipsConnector.createMtdItRelationship(invitation)
      case Service.PersonalIncomeRecord => relationshipsConnector.createAfiRelationship(invitation, acceptedDate)
      case Service.Vat                  => relationshipsConnector.createMtdVatRelationship(invitation)
      case Service.Trust                => relationshipsConnector.createTrustRelationship(invitation)
      case Service.CapitalGains         => relationshipsConnector.createCapitalGainsRelationship(invitation)
    }

    createRelationship.recover {
      case e if e.getMessage.contains("RELATIONSHIP_ALREADY_EXISTS") =>
        Logger.warn(
          s"Error Found: ${e.getMessage} \n Client has accepted an invitation despite previously delegated the same agent")
    }
  }

  def findInvitationsInfoBy(arn: Arn, clientIds: Seq[(String, String, String)], status: Option[InvitationStatus])(
    implicit ec: ExecutionContext): Future[List[InvitationInfo]] =
    invitationsRepository.findInvitationInfoBy(arn, clientIds, status)

  def cancelInvitation(invitation: Invitation)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier): Future[Either[StatusUpdateFailure, Invitation]] =
    changeInvitationStatus(invitation, model.Cancelled)
      .andThen {
        case Success(result) => {
          reportHistogramValue("Duration-Invitation-Cancelled", durationOf(invitation))
          result match {
            case Right(i) => analyticsService.reportSingleEventAnalyticsRequest(i)
            case Left(_)  => Future.successful(())
          }
        }
      }

  def setRelationshipEnded(
    invitation: Invitation)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Invitation] =
    monitor(s"Repository-Change-Invitation-${invitation.service.id}-flagRelationshipEnded") {
      invitationsRepository.setRelationshipEnded(invitation) map { invitation =>
        Logger info s"""Invitation with id: "${invitation.id.stringify}" has been flagged as isRelationshipEnded = true"""
        invitation
      }
    }

  def rejectInvitation(invitation: Invitation)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier): Future[Either[StatusUpdateFailure, Invitation]] = {
    def changeStatus: Future[Either[StatusUpdateFailure, Invitation]] =
      changeInvitationStatus(invitation, model.Rejected)
        .andThen {
          case Success(_) => reportHistogramValue("Duration-Invitation-Rejected", durationOf(invitation))
        }
    for {
      result <- changeStatus
      _ <- result match {
            case Right(invite) => {
              emailService
                .sendRejectedEmail(invite)
                .map(_ => analyticsService.reportSingleEventAnalyticsRequest(invite))
            }
            case Left(_) => Future.successful(())
          }
    } yield result
  }

  def findInvitation(invitationId: InvitationId)(
    implicit ec: ExecutionContext,
    hc: HeaderCarrier,
    request: Request[Any]): Future[Option[Invitation]] =
    monitor(s"Repository-Find-Invitation-${invitationId.value.charAt(0)}") {
      invitationsRepository.findByInvitationId(invitationId)
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
    createdOnOrAfter: Option[LocalDate] = None)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[List[Invitation]] = {
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

  def getNonSuspendedInvitations(identifiers: Seq[(Service, String)])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Seq[List[InvitationInfo]]] = {

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
      invs <- Future.sequence(invitations.flatMap(invs =>
               invs.map(inv => isArnSuspendedForService(inv.arn, inv.service).map(isSuspended => (invs, isSuspended)))))
    } yield invs.filter(!_._2.getOrElse(false)).map(_._1)
  }

  def findAndUpdateExpiredInvitations()(implicit ec: ExecutionContext): Future[Unit] =
    monitor(s"Repository-Find-And-Update-Expired-Invitations") {
      invitationsRepository
        .findInvitationsBy(status = Some(Pending))
        .map { invitations =>
          invitations.foreach { invitation =>
            if (invitation.expiryDate.isBefore(LocalDate.now())) {
              invitationsRepository
                .update(invitation, Expired, DateTime.now())
                .flatMap(invitation => {
                  Logger(getClass).info(s"invitation expired id:${invitation.invitationId.value}")
                  emailService.sendExpiredEmail(invitation)
                })
            }
          }
        }
    }

  private def changeInvitationStatus(
    invitation: Invitation,
    status: InvitationStatus,
    timestamp: DateTime = currentTime())(
    implicit ec: ExecutionContext): Future[Either[StatusUpdateFailure, Invitation]] =
    invitation.status match {
      case Pending =>
        monitor(s"Repository-Change-Invitation-${invitation.service.id}-Status-From-${invitation.status}-To-$status") {
          invitationsRepository.update(invitation, status, timestamp) map { invitation =>
            Logger info s"""Invitation with id: "${invitation.id.stringify}" has been $status"""
            Right(invitation)
          }
        }
      case _ => Future successful cannotTransitionBecauseNotPending(invitation, status)
    }

  private def cannotTransitionBecauseNotPending(invitation: Invitation, toStatus: InvitationStatus) =
    Left(
      StatusUpdateFailure(
        invitation.status,
        s"The invitation cannot be transitioned to $toStatus because its current status is ${invitation.status}. Only Pending invitations may be transitioned to $toStatus."
      ))

  private def currentTime() = DateTime.now(DateTimeZone.UTC)

  private def durationOf(invitation: Invitation): Long =
    if (invitation.events.isEmpty) 0
    else
      System.currentTimeMillis() - invitation.firstEvent().time.getMillis

  def removeAllInvitationsForAgent(arn: Arn)(implicit ec: ExecutionContext): Future[Int] =
    invitationsRepository.removeAllInvitationsForAgent(arn)
}
