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

import play.api.hal.{HalLink, HalLinks, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Action
import play.api.mvc.hal.halWriter
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{SaClientRequest, AgentRequest, AuthActions}
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationHttpRequest, AgentClientAuthorisationRequest}
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.agentclientauthorisation.sa.services.SaLookupService
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class AuthorisationRequestController(authorisationRequestRepository: AuthorisationRequestRepository, saLookupService: SaLookupService, override val authConnector: AuthConnector)
  extends BaseController with AuthActions {

  def createRequest() = onlyForAgents.async(parse.json) { implicit request =>
    withJsonBody[AgentClientAuthorisationHttpRequest] { authRequest: AgentClientAuthorisationHttpRequest =>
      saLookupService.utrAndPostcodeMatch(authRequest.clientSaUtr, authRequest.clientPostcode) flatMap { utrAndPostcodeMatch =>
        if (utrAndPostcodeMatch) {
          // TODO Audit
          authorisationRequestRepository.create(request.agentCode, authRequest.clientSaUtr) map { _ => Created }
        } else {
          // TODO Audit failure including UTR and postcode
          Future successful Forbidden("No SA taxpayer found with the given UTR and postcode")
        }
      }
    }
  }

  def getRequests() = onlyForAgents.async { implicit request =>
    authorisationRequestRepository.list(request.agentCode).map(toHalResource).map(Ok(_)(halWriter))
  }

  private def toHalResource(requests: List[AgentClientAuthorisationRequest]): HalResource = {
    val links = HalLinks(Vector(HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests.url)))
    val requestResources: Vector[HalResource] = requests.map(toHalResource).toVector
    HalResource(links, JsObject(Seq.empty), Vector("requests" -> requestResources))
  }

  private def toHalResource(request: AgentClientAuthorisationRequest): HalResource = {
    val links = HalLinks(Vector(HalLink("self", s"${uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests.url}/${request.id}")))
    HalResource(links, Json.toJson(request).as[JsObject])
  }

}
