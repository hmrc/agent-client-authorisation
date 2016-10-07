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

package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.hal.{Hal, HalLink, HalLinks, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Action
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationHttpRequest, Arn, Invitation}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class InvitationsController(invitationsRepository: InvitationsRepository,
                            postcodeService: PostcodeService,
                            override val authConnector: AuthConnector,
                            override val agenciesFakeConnector: AgenciesFakeConnector) extends BaseController with AuthActions {

  private val SUPPORTED_REGIME = "mtd-sa"

  def createInvitation(arn: Arn) = onlyForSaAgents.async(parse.json) { implicit request =>
    withJsonBody[AgentClientAuthorisationHttpRequest] { authRequest =>
      if (!supportedRegime(authRequest.regime))
        Future successful NotImplemented(errorResponseBody(
          code = "UNSUPPORTED_REGIME",
          message = s"""Unsupported regime "${authRequest.regime}", the only currently supported regime is "$SUPPORTED_REGIME""""
        ))
      else if (!validPostcode(authRequest.postcode))
        Future successful BadRequest
      else if (!postcodeService.customerPostcodeMatches(authRequest.customerRegimeId, authRequest.postcode))
        Future successful Forbidden
      else
        invitationsRepository.create(arn, authRequest.regime, authRequest.customerRegimeId, authRequest.postcode)
          .map(invitation => Created.withHeaders(location(invitation)))
    }
  }

  private def location(invitation: Invitation) = {
    "location" -> routes.InvitationsController.getSentInvitation(invitation.arn, invitation.id.stringify).url
  }

  def getSentInvitations(arn: Arn) = onlyForSaAgents.async {
    invitationsRepository.list(arn).map { invitations =>
      Ok(Json.toJson(toHalResource(invitations, arn)))
    }
  }

  def getSentInvitation(arn: Arn, invitation: String) = Action.async {
    Future successful NotImplemented
  }

  private def toHalResource(requests: List[Invitation], arn: Arn): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource(_, arn)).toVector

    Hal.embedded("invitations", requestResources:_*) ++
      HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.InvitationsController.getSentInvitations(arn).url)
  }

  private def toHalResource(request: Invitation, arn: Arn): HalResource = {
    val links = HalLinks(Vector(HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.InvitationsController.getSentInvitation(arn, request.id.stringify).url)))
    HalResource(links, Json.toJson(request).as[JsObject])
  }

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r
  private def validPostcode(postcode: String) = postcodeWithoutSpacesRegex.findFirstIn(postcode.replaceAll(" ", "")).isDefined

  private def supportedRegime(regime: String) = SUPPORTED_REGIME == regime

  private def errorResponseBody(code: String, message: String) =
    Json.obj(
      "code" -> code,
      "message" -> message
    )
}
