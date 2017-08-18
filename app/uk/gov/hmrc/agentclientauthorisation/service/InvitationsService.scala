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

import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{ClientIdMappingRepository, InvitationsRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsService @Inject()(invitationsRepository: InvitationsRepository,
                                   clientIdMappingRepository: ClientIdMappingRepository,
                                   relationshipsConnector: RelationshipsConnector,
                                   desConnector: DesConnector) {
  def translateToMtdItId(clientId: String, clientIdType: String)
                        (implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[MtdItId]] = {
    clientIdType match {
      case "MTDITID" => Future successful Some(MtdItId(clientId))
      case "ni" =>

        // solution 1
        (for {
          list <- clientIdMappingRepository.find(clientId, clientIdType)
          businessDetails <- desConnector.getBusinessDetails(Nino(clientId))
        } yield (list, businessDetails))
          .map({
            case (x :: tail, _) => Some(MtdItId(x.canonicalClientId))
            case (Nil, Some(record)) => record.mtdbsa
            case (Nil, None) => None
          }).recover { case _ => None }


        // solution 2
        clientIdMappingRepository.find(clientId, clientIdType).flatMap({
          case x :: tail => Future successful Some(MtdItId(x.canonicalClientId))
          case Nil => desConnector.getBusinessDetails(Nino(clientId)).map({
            case Some(record) => record.mtdbsa
            case None => None
          })
        })

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

  def findInvitation(invitationId: String)(implicit ec: ExecutionContext): Future[Option[Invitation]] =
    BSONObjectID.parse(invitationId)
      .map(bsonInvitationId => invitationsRepository.findById(bsonInvitationId))
      .recover { case _: IllegalArgumentException => Future successful None }
      .get


  def clientsReceived(service: String, clientId: MtdItId, status: Option[InvitationStatus])
                     (implicit ec: ExecutionContext): Future[Seq[Invitation]] =
    invitationsRepository.list(service, clientId, status)

  def agencySent(arn: Arn, service: Option[String], clientIdType: Option[String], clientId: Option[String], status: Option[InvitationStatus])
                (implicit ec: ExecutionContext): Future[List[Invitation]] =
    if (clientIdType.getOrElse("ni") == "ni")
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
