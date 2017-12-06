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

import javax.inject._

import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject()(invitationsRepository: InvitationsRepository,
                                   relationshipsConnector: RelationshipsConnector, desConnector: DesConnector) {
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

  def create(arn: Arn, service: String, clientId: MtdItId, postcode: String, suppliedClientId: String, suppliedClientIdType: String)
            (implicit ec: ExecutionContext): Future[Invitation] =
    invitationsRepository.create(arn, service, clientId, postcode, suppliedClientId, suppliedClientIdType)


  def acceptInvitation(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[String, Invitation]] = {
    if (invitation.status == Pending) {
      relationshipsConnector.createRelationship(invitation.arn, MtdItId(invitation.clientId))
        .flatMap(_ => changeInvitationStatus(invitation, model.Accepted))
    } else {
      Future successful cannotTransitionBecauseNotPending(invitation, Accepted)
    }
  }

  def cancelInvitation(invitation: Invitation)(implicit ec: ExecutionContext): Future[Either[String, Invitation]] =
    changeInvitationStatus(invitation, model.Cancelled)

  def rejectInvitation(invitation: Invitation)(implicit ec: ExecutionContext): Future[Either[String, Invitation]] =
    changeInvitationStatus(invitation, model.Rejected)

  def findInvitation(invitationId: InvitationId)(implicit ec: ExecutionContext): Future[Option[Invitation]] = {
    invitationsRepository.find("invitationId" -> invitationId)
      .map(_.headOption)
  }

  def clientsReceived(service: String, clientId: MtdItId, status: Option[InvitationStatus])
                     (implicit ec: ExecutionContext): Future[Seq[Invitation]] =
    invitationsRepository.list(service, clientId, status)

  def agencySent(arn: Arn, service: Option[String], clientIdType: Option[String], clientId: Option[String], status: Option[InvitationStatus])
                (implicit ec: ExecutionContext): Future[List[Invitation]] =
    if (clientIdType.getOrElse(CLIENT_ID_TYPE_NINO) == CLIENT_ID_TYPE_NINO)
      invitationsRepository.list(arn, service, clientId, status)
    else Future successful List.empty

  private def changeInvitationStatus(invitation: Invitation, status: InvitationStatus)
                                    (implicit ec: ExecutionContext): Future[Either[String, Invitation]] = {
    invitation.status match {
      case Pending => invitationsRepository.update(invitation.id, status) map (invitation => Right(invitation))
      case _ => Future successful cannotTransitionBecauseNotPending(invitation, status)
    }
  }

  private def cannotTransitionBecauseNotPending(invitation: Invitation, toStatus: InvitationStatus) = {
    Left(s"The invitation cannot be transitioned to $toStatus because its current status is ${invitation.status}. Only Pending invitations may be transitioned to $toStatus.")
  }
}
