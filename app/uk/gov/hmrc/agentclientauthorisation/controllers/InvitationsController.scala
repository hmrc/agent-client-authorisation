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
import javax.inject._
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, MicroserviceAuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.InvitationNotFound
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AgentInvitationValidation
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

@Singleton
class InvitationsController @Inject()(
  invitationsService: InvitationsService,
  microserviceAuthConnector: MicroserviceAuthConnector,
  metrics: Metrics)
    extends AuthActions(metrics, microserviceAuthConnector) with HalWriter with AgentInvitationValidation
    with AgencyInvitationsHal {

  def getInvitationById(invitationId: InvitationId): Action[AnyContent] = Action.async { implicit request =>
    authorised() {
      invitationsService.findInvitation(invitationId).map {
        _.map(invitation => Ok(toHalResource(invitation))) getOrElse InvitationNotFound
      }
    }
  }

  override protected def agencyLink(invitation: Invitation) = None
}
