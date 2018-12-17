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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Provider}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatus
import uk.gov.hmrc.agentclientauthorisation.repository.AgentReferenceRepository
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AgentReferenceController @Inject()(
  agentReferenceRecordRepository: AgentReferenceRepository,
  invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, authConnector) {

  implicit val ec: ExecutionContext = ecp.get

  def getAgentReferenceRecord(uid: String): Action[AnyContent] = Action.async { implicit request =>
    agentReferenceRecordRepository
      .findBy(uid)
      .map {
        case Some(multiInvitationRecord) => Ok(Json.toJson(multiInvitationRecord))
        case None =>
          Logger(getClass).warn(s"Agent Reference Record not found for: $uid")
          NotFound
      }
      .recoverWith {
        case e =>
          Future failed (throw new Exception(s"Something has gone wrong for: $uid. Error found: ${e.getMessage}"))
      }
  }

  def getInvitationsInfo(uid: String, status: Option[InvitationStatus]): Action[AnyContent] = Action.async {
    implicit request =>
      withMultiEnrolledClient { implicit clientIds =>
        for {
          recordOpt <- agentReferenceRecordRepository.findBy(uid)
          result <- recordOpt match {
                     case Some(record) =>
                       invitationsService
                         .findInvitationsInfoBy(record.arn, clientIds, status)
                         .map(list => Ok(Json.toJson(list)))
                     case _ => Future successful NotFound
                   }
        } yield result
      }
  }
}
