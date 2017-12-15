/*
 * Copyright 2017 HM Revenue & Customs
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

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit.DAYS
import javax.inject._

import org.joda.time.{DateTime, DateTimeZone}
import play.api.mvc.Request
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

import scala.concurrent.duration
import scala.concurrent.duration.Duration
import uk.gov.hmrc.http.HeaderCarrier


case class StatusUpdateFailure(currentStatus: InvitationStatus, failureReason: String)

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject()( invitationsRepository: InvitationsRepository,
                                   relationshipsConnector: RelationshipsConnector,
                                   desConnector: DesConnector,
                                   auditService: AuditService,
                                   @Named("invitation.expiryDuration") invitationExpiryDurationValue: String) {

  private val invitationExpiryDuration = Duration(invitationExpiryDurationValue.replace('_', ' '))
  private val invitationExpiryUnits = invitationExpiryDuration.unit

  def translateToMtdItId(clientId: String, clientIdType: String)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]] = {
    clientIdType match {
      case CLIENT_ID_TYPE_MTDITID => Future successful Some(MtdItId(clientId))
      case CLIENT_ID_TYPE_NINO =>
        desConnector.getBusinessDetails(Nino(clientId)).map{
          case Some(record) => record.mtdbsa
          case None => None
        }
      case _ => Future successful None
    }
  }

  def create(arn: Arn, service: Service, clientId: TaxIdentifier, postcode: Option[String], suppliedClientId: String, suppliedClientIdType: String)
            (implicit ec: ExecutionContext): Future[Invitation] =
    invitationsRepository.create(arn, service, clientId, postcode, suppliedClientId, suppliedClientIdType)

  def acceptInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[StatusUpdateFailure, Invitation]] = {
    val acceptedDate = currentTime()
    invitation.status match {
      case Pending => {
        val future = invitation.service match {
          case Service.MtdIt => relationshipsConnector.createRelationship(invitation)
          case Service.PersonalIncomeRecord => relationshipsConnector.createAfiRelationship(invitation, acceptedDate)
        }
        future.flatMap(_ => changeInvitationStatus(invitation, model.Accepted, acceptedDate))
      }
      case _ => Future successful cannotTransitionBecauseNotPending(invitation, Accepted)
    }
  }

  private[service] def isInvitationExpired(invitation: Invitation, currentDateTime: () => DateTime = currentTime) = {
    val createTime = invitation.firstEvent.time
    val fromTime = if (invitationExpiryUnits == DAYS) createTime.millisOfDay().withMinimumValue() else createTime
    val elapsedTime = Duration.create(currentDateTime().getMillis - fromTime.getMillis, duration.MILLISECONDS)
    elapsedTime gt invitationExpiryDuration
  }

  private def updateStatusToExpired(invitation: Invitation)(implicit ec: ExecutionContext,
                                                            hc: HeaderCarrier, request: Request[Any]): Future[Invitation] = {
    changeInvitationStatus(invitation, Expired).map { a =>
      if (a.isLeft) throw new Exception("Failed to transition invitation state to Expired")
      auditService.sendInvitationExpired(invitation)
      a.right.get
    }
  }

  def cancelInvitation(invitation: Invitation)(implicit ec: ExecutionContext): Future[Either[StatusUpdateFailure, Invitation]] =
    changeInvitationStatus(invitation, model.Cancelled)

  def rejectInvitation(invitation: Invitation)(implicit ec: ExecutionContext): Future[Either[StatusUpdateFailure, Invitation]] =
    changeInvitationStatus(invitation, model.Rejected)

  def findInvitation(invitationId: InvitationId)(implicit ec: ExecutionContext, hc: HeaderCarrier,
                                           request: Request[Any]): Future[Option[Invitation]] = {
    invitationsRepository.find("invitationId" -> invitationId)
      .map(_.headOption)
      .flatMap { invitationResult =>
        if (invitationResult.isEmpty) Future successful None else {
          val invitation = invitationResult.get
          if (isInvitationExpired(invitation)) updateStatusToExpired(invitation).map(Some(_))
          else Future successful invitationResult
        }
      }
  }

  def clientsReceived(service: Service, clientId: TaxIdentifier, status: Option[InvitationStatus])
                     (implicit ec: ExecutionContext): Future[Seq[Invitation]] =
    invitationsRepository.list(service, clientId, status)

  def agencySent(arn: Arn, service: Option[Service], clientIdType: Option[String], clientId: Option[String], status: Option[InvitationStatus])
                (implicit ec: ExecutionContext): Future[List[Invitation]] =
    if (clientIdType.getOrElse(CLIENT_ID_TYPE_NINO) == CLIENT_ID_TYPE_NINO)
      invitationsRepository.list(arn, service, clientId, status)
    else Future successful List.empty

  private def changeInvitationStatus(invitation: Invitation, status: InvitationStatus, timestamp: DateTime = currentTime())
                                    (implicit ec: ExecutionContext): Future[Either[StatusUpdateFailure, Invitation]] = {
    invitation.status match {
      case Pending => invitationsRepository.update(invitation.id, status, timestamp) map (invitation => Right(invitation))
      case _ => Future successful cannotTransitionBecauseNotPending(invitation, status)
    }
  }

  private def cannotTransitionBecauseNotPending(invitation: Invitation, toStatus: InvitationStatus) = {
    Left(StatusUpdateFailure(invitation.status, s"The invitation cannot be transitioned to $toStatus because its current status is ${invitation.status}. Only Pending invitations may be transitioned to $toStatus."))
  }

  private def currentTime() = DateTime.now(DateTimeZone.UTC)
}
