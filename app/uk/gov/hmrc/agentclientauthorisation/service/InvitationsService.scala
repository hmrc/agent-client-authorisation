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

package uk.gov.hmrc.agentclientauthorisation.service

import java.util.concurrent.TimeUnit.DAYS

import javax.inject._
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.Logger
import play.api.mvc.Request
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{
  DesConnector,
  RelationshipsConnector
}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{
  InvitationsRepository,
  Monitor
}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.duration
import scala.concurrent.duration.Duration
import scala.util.Success

case class StatusUpdateFailure(currentStatus: InvitationStatus,
                               failureReason: String)

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject()(
    invitationsRepository: InvitationsRepository,
    relationshipsConnector: RelationshipsConnector,
    desConnector: DesConnector,
    auditService: AuditService,
    @Named("invitation.expiryDuration") invitationExpiryDurationValue: String,
    metrics: Metrics)
    extends Monitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val invitationExpiryDuration = Duration(
    invitationExpiryDurationValue.replace('_', ' '))
  private val invitationExpiryUnits = invitationExpiryDuration.unit

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

  def create(arn: Arn,
             service: Service,
             clientId: ClientId,
             postcode: Option[String],
             suppliedClientId: ClientId)(
      implicit ec: ExecutionContext): Future[Invitation] = {
    val startDate = currentTime()
    val expiryDate =
      startDate.plus(invitationExpiryDuration.toMillis).toLocalDate
    monitor(s"Repository-Create-Invitation-${service.id}") {
      invitationsRepository
        .create(arn,
                service,
                clientId,
                suppliedClientId,
                postcode,
                startDate,
                expiryDate)
        .map { invitation =>
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
      case Pending => {
        val future = invitation.service match {
          case Service.MtdIt =>
            relationshipsConnector.createMtdItRelationship(invitation)
          case Service.PersonalIncomeRecord =>
            relationshipsConnector.createAfiRelationship(invitation,
                                                         acceptedDate)
          case Service.Vat =>
            relationshipsConnector.createMtdVatRelationship(invitation)
        }
        future
          .flatMap(
            _ =>
              changeInvitationStatus(invitation, model.Accepted, acceptedDate)
                .andThen {
                  case Success(_) =>
                    reportHistogramValue("Duration-Invitation-Accepted",
                                         durationOf(invitation))
              })
          .recoverWith {
            case e if e.getMessage.contains("RELATIONSHIP_ALREADY_EXISTS") =>
              Logger.warn(
                s"Error Found: ${e.getMessage} \n Client has accepted an invitation despite previously delegated the same agent")
              changeInvitationStatus(invitation, model.Accepted, acceptedDate)
                .andThen {
                  case Success(_) =>
                    reportHistogramValue("Duration-Invitation-Accepted-Again",
                                         durationOf(invitation))
                }
          }
      }
      case _ =>
        Future successful cannotTransitionBecauseNotPending(invitation,
                                                            Accepted)
    }
  }

  private[service] def isInvitationExpired(invitation: Invitation,
                                           currentDateTime: () => DateTime =
                                             currentTime) = {
    val createTime = invitation.firstEvent().time
    val fromTime =
      if (invitationExpiryUnits == DAYS)
        createTime.millisOfDay().withMinimumValue()
      else createTime
    val elapsedTime = Duration.create(
      currentDateTime().getMillis - fromTime.getMillis,
      duration.MILLISECONDS)
    elapsedTime gt invitationExpiryDuration
  }

  private def updateStatusToExpired(invitation: Invitation)(
      implicit
      ec: ExecutionContext,
      hc: HeaderCarrier,
      request: Request[Any]): Future[Invitation] =
    changeInvitationStatus(invitation, Expired).map { a =>
      if (a.isLeft)
        throw new Exception("Failed to transition invitation state to Expired")
      auditService.sendInvitationExpired(invitation)
      a.right.get
    } andThen {
      case Success(_) =>
        reportHistogramValue("Duration-Invitation-Expired",
                             durationOf(invitation))
    }

  def cancelInvitation(invitation: Invitation)(implicit ec: ExecutionContext)
    : Future[Either[StatusUpdateFailure, Invitation]] =
    changeInvitationStatus(invitation, model.Cancelled)
      .andThen {
        case Success(_) =>
          reportHistogramValue("Duration-Invitation-Cancelled",
                               durationOf(invitation))
      }

  def rejectInvitation(invitation: Invitation)(implicit ec: ExecutionContext)
    : Future[Either[StatusUpdateFailure, Invitation]] =
    changeInvitationStatus(invitation, model.Rejected)
      .andThen {
        case Success(_) =>
          reportHistogramValue("Duration-Invitation-Rejected",
                               durationOf(invitation))
      }

  def findInvitation(invitationId: InvitationId)(
      implicit ec: ExecutionContext,
      hc: HeaderCarrier,
      request: Request[Any]): Future[Option[Invitation]] =
    monitor(s"Repository-Find-Invitation-${invitationId.value.charAt(0)}") {
      invitationsRepository.find("invitationId" -> invitationId)
    }.map(_.headOption)
      .flatMap { invitationResult =>
        if (invitationResult.isEmpty) Future successful None
        else {
          val invitation = invitationResult.get
          if (isInvitationExpired(invitation))
            updateStatusToExpired(invitation).map(Some(_))
          else Future successful invitationResult
        }
      }

  def clientsReceived(service: Service,
                      clientId: ClientId,
                      status: Option[InvitationStatus])(
      implicit ec: ExecutionContext): Future[Seq[Invitation]] =
    monitor(
      s"Repository-List-Invitations-Received-$service${status.map(s => s"-$s").getOrElse("")}") {
      invitationsRepository.list(service, clientId, status)
    }

  def agencySent(arn: Arn,
                 service: Option[Service],
                 clientIdType: Option[String],
                 clientId: Option[String],
                 status: Option[InvitationStatus],
                 createdOnOrAfter: Option[LocalDate])(
      implicit ec: ExecutionContext): Future[List[Invitation]] =
    if (clientIdType.getOrElse(NinoType.id) == NinoType.id)
      monitor(s"Repository-List-Invitations-Sent${service
        .map(s => s"-${s.id}")
        .getOrElse("")}${status.map(s => s"-$s").getOrElse("")}") {
        invitationsRepository.list(arn,
                                   service,
                                   clientId,
                                   status,
                                   createdOnOrAfter)
      } else Future successful List.empty

  private def changeInvitationStatus(
      invitation: Invitation,
      status: InvitationStatus,
      timestamp: DateTime = currentTime())(implicit ec: ExecutionContext)
    : Future[Either[StatusUpdateFailure, Invitation]] =
    invitation.status match {
      case Pending =>
        monitor(
          s"Repository-Change-Invitation-${invitation.service.id}-Status-From-${invitation.status}-To-$status") {
          invitationsRepository.update(invitation.id, status, timestamp) map {
            invitation =>
              Logger info s"""Invitation with id: "${invitation.id.stringify}" has been $status"""
              Right(invitation)
          }
        }
      case _ =>
        Future successful cannotTransitionBecauseNotPending(invitation, status)
    }

  private def cannotTransitionBecauseNotPending(invitation: Invitation,
                                                toStatus: InvitationStatus) =
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
}
