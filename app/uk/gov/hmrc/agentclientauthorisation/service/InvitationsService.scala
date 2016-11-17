/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

@Singleton
class InvitationsService @Inject() (invitationsRepository: InvitationsRepository,
                         relationshipsConnector: RelationshipsConnector) {

  def create(arn: Arn, regime: String, clientId: String, postcode: String) =
    invitationsRepository.create(arn, regime, clientId, postcode)


  def acceptInvitation(invitation: Invitation)(implicit hc: HeaderCarrier): Future[Boolean] = {
    if (invitation.status == Pending) {
      relationshipsConnector.createRelationship(invitation.arn, MtdClientId(invitation.clientId))
        .flatMap(_ => changeInvitationStatus(invitation, model.Accepted))
    } else {
      Future successful false
    }
  }

  def cancelInvitation(invitation: Invitation): Future[Boolean] =
      changeInvitationStatus(invitation, model.Cancelled)

  def rejectInvitation(invitation: Invitation): Future[Boolean] =
      changeInvitationStatus(invitation, model.Rejected)

  def findInvitation(invitationId: String): Future[Option[Invitation]] =
      invitationsRepository.findById(BSONObjectID(invitationId))

  def clientsReceived(regime: String, clientId: String, status: Option[InvitationStatus]): Future[Seq[Invitation]] =
    invitationsRepository.list(regime, clientId, status)

  def agencySent(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]): Future[List[Invitation]] =
    invitationsRepository.list(arn, regime, clientId, status)

  private def changeInvitationStatus(invitation: Invitation, status: InvitationStatus): Future[Boolean] = {
    invitation.status match {
      case Pending => invitationsRepository.update(invitation.id, status) map (_ => true)
      case _ => Future successful false
    }
  }
}
