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

package uk.gov.hmrc.agentclientauthorisation.controllers

import javax.inject._

import play.api.hal.{Hal, HalLink, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.language.postfixOps

@Singleton
class RootController @Inject() (override val agenciesFakeConnector: AgenciesFakeConnector,
                                override val authConnector: AuthConnector)
    extends BaseController with AuthActions with HalWriter {
  private val selfLink = Vector(HalLink("self", routes.RootController.getRootResource().url))

  def getRootResource() = withAccounts.async { implicit request =>
    (request.accounts.agent, request.accounts.sa) match {
      case (Some(agentCode), _) => agenciesFakeConnector.findArn(agentCode).map(_.map(arn => Ok(toHalResource(arn))).getOrElse(AgentRegistrationNotFound))
      case (None, Some(saUtr))  => agenciesFakeConnector.findClient(saUtr) .map(_.map(clientId => Ok(toHalResource(clientId))).getOrElse(ClientRegistrationNotFound))
      case (None, None)         => Future successful SaEnrolmentNotFound
    }
  }

  private def toHalResource(arn: Arn): HalResource = {
    val invitationsSentLink = Vector(HalLink("sent", routes.AgencyInvitationsController.getSentInvitations(arn, None, None, None).url))
    Hal.hal(Json.obj(), selfLink ++ invitationsSentLink, Vector())
  }

  private def toHalResource(nino: Nino): HalResource = {
    val invitationsReceivedLink = Vector(HalLink("received", routes.ClientInvitationsController.getInvitations(nino.value, None).url))
    Hal.hal(Json.obj(), selfLink ++ invitationsReceivedLink, Vector())
  }
}
