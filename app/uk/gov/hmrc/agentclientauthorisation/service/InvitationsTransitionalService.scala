/*
 * Copyright 2025 HM Revenue & Customs
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

import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.RelationshipsConnector
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsTransitionalService @Inject() (
  invitationsRepository: InvitationsRepository,
  relationshipsConnector: RelationshipsConnector,
  appConfig: AppConfig
) {

  def findInvitation(invitationId: InvitationId)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Option[Invitation]] =
    if (appConfig.acrMongoActivated) {
      relationshipsConnector.lookupInvitation(invitationId.value).flatMap {
        case Some(invitation) => Future.successful(Some(invitation))
        case None             => invitationsRepository.findByInvitationId(invitationId)
      }
    } else {
      invitationsRepository.findByInvitationId(invitationId)
    }
}
