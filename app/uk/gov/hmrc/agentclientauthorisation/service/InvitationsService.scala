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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.RelationshipsConnector
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class InvitationsService @Inject() (invitationsRepository: InvitationsRepository,
                         relationshipsConnector: RelationshipsConnector) {

  def create(arn: Arn, service: String, clientId: String, postcode: String) =
    invitationsRepository.create(arn, service, clientId, postcode)


  def acceptInvitation(invitation: Invitation)(implicit hc: HeaderCarrier): Future[Either[String, Invitation]] = {
    if (invitation.status == Pending) {
      relationshipsConnector.createRelationship(invitation.arn, Nino(invitation.clientId))
        .flatMap(_ => changeInvitationStatus(invitation, model.Accepted))
    } else {
      Future successful cannotTransitionBecauseNotPending(invitation, Accepted)
    }
  }

  def cancelInvitation(invitation: Invitation): Future[Either[String, Invitation]] =
    changeInvitationStatus(invitation, model.Cancelled)

  def rejectInvitation(invitation: Invitation): Future[Either[String, Invitation]] =
    changeInvitationStatus(invitation, model.Rejected)

  def findInvitation(invitationId: String): Future[Option[Invitation]] =
    BSONObjectID.parse(invitationId)
      .map(bsonInvitationId => invitationsRepository.findById(bsonInvitationId))
      .recover { case _: IllegalArgumentException => Future successful None }
      .get


  def clientsReceived(service: String, clientId: String, status: Option[InvitationStatus]): Future[Seq[Invitation]] =
    invitationsRepository.list(service, clientId, status)

  def agencySent(arn: Arn, service: Option[String], clientId: Option[String], status: Option[InvitationStatus]): Future[List[Invitation]] =
    invitationsRepository.list(arn, service, clientId, status)

  private def changeInvitationStatus(invitation: Invitation, status: InvitationStatus): Future[Either[String, Invitation]] = {
    invitation.status match {
      case Pending => invitationsRepository.update(invitation.id, status) map (invitation => Right(invitation))
      case _ => Future successful cannotTransitionBecauseNotPending(invitation, status)
    }
  }

  private def cannotTransitionBecauseNotPending(invitation: Invitation, toStatus: InvitationStatus) = {
    Left(s"The invitation cannot be transitioned to $toStatus because its current status is ${invitation.status}. Only Pending invitations may be transitioned to $toStatus.")
  }
}
