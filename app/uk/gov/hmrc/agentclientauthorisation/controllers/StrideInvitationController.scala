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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Provider, Singleton}
import play.api.Logger
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthActions
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.genericServiceUnavailable

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

@Singleton
class StrideInvitationController @Inject()(
  appConfig: AppConfig,
  invitationsRepository: InvitationsRepository,
  agentReferenceRepository: AgentReferenceRepository,
  authConnector: AuthConnector)(
  implicit
  metrics: Metrics,
  cc: ControllerComponents,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, authConnector, cc) {

  implicit val ec: ExecutionContext = ecp.get

  def removeAllInvitationsAndReferenceForArn(arn: Arn): Action[AnyContent] = onlyStride { implicit request =>
    (for {
      invitationsDeleted <- invitationsRepository.removeAllInvitationsForAgent(arn)
      referencesDeleted  <- agentReferenceRepository.removeAgentReferencesForGiven(arn)
    } yield
      Ok(Json
        .obj("arn" -> arn.value, "InvitationsDeleted" -> invitationsDeleted, "ReferencesDeleted" -> referencesDeleted)))
      .recover {
        case e => {
          Logger(getClass).warn(s"Something has gone for ${arn.value} due to: ${e.getMessage}")
          genericServiceUnavailable(e.getMessage)
        }
      }
  }

}
