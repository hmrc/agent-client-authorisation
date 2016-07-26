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

import play.api.Logger
import play.api.hal.{HalLink, HalLinks, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Action
import play.api.mvc.hal.halWriter
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationHttpRequest, AgentClientAuthorisationRequest}
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.agentclientauthorisation.sa.services.SaLookupService
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.microservice.controller.BaseController

class AuthorisationRequestController(authorisationRequestRepository: AuthorisationRequestRepository, saLookupService: SaLookupService)
  extends BaseController {

  def createRequest(agentCode: AgentCode) = Action.async(parse.json) { implicit request =>
    withJsonBody[AgentClientAuthorisationHttpRequest] { authRequest: AgentClientAuthorisationHttpRequest =>
      saLookupService.utrAndPostcodeMatch(authRequest.clientSaUtr, authRequest.clientPostcode) map { utrAndPostcodeMatch =>
        if (utrAndPostcodeMatch) {
          // TODO Audit
          //TODO fix race condition, probably with authorisationRequestRepository.create(agentCode, authRequest.clientSaUtr) map { _ => Created }
          authorisationRequestRepository.create(agentCode, authRequest.clientSaUtr)
          Created //TODO Location header?
        } else {
          // TODO Audit including postcode as well as UTR (don't think we are allowed to log postcode)
          Logger.warn(s"createRequest not authorised because postcode does not match for UTR ${authRequest.clientSaUtr}")
          Forbidden("No SA taxpayer found with the given UTR and postcode")
        }
      }
    }
  }

  def getRequests(agentCode: AgentCode) = Action.async { implicit request =>
    authorisationRequestRepository.list(agentCode).map(toHalResource(agentCode, _)).map(Ok(_)(halWriter))
  }

  private def toHalResource(agentCode: AgentCode, requests: List[AgentClientAuthorisationRequest]): HalResource = {
    val links = HalLinks(Vector(HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests(agentCode).url)))
    val requestResources: Vector[HalResource] = requests.map(toHalResource(agentCode, _)).toVector
    HalResource(links, JsObject(Seq.empty), Vector("requests" -> requestResources))
  }

  private def toHalResource(agentCode: AgentCode, request: AgentClientAuthorisationRequest): HalResource = {
    val links = HalLinks(Vector(HalLink("self", s"${uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests(agentCode).url}/${request.id}")))
    HalResource(links, Json.toJson(request).as[JsObject])
  }

}
