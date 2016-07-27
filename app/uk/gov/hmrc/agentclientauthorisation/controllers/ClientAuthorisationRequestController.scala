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
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationHttpRequest, AgentClientAuthorisationRequest}
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class ClientAuthorisationRequestController(authorisationRequestRepository: AuthorisationRequestRepository) extends BaseController {

  def getRequests(saUtr: SaUtr) = Action.async { implicit request =>
    authorisationRequestRepository.list(saUtr).map(toHalResource(saUtr, _)).map(Ok(_)(halWriter))
  }


  private def toHalResource(saUtr: SaUtr, requests: List[AgentClientAuthorisationRequest]): HalResource = {
    val links = HalLinks(Vector(HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.ClientAuthorisationRequestController.getRequests(saUtr).url)))
    val requestResources: Vector[HalResource] = requests.map(toHalResource(saUtr, _)).toVector
    HalResource(links, JsObject(Seq.empty), Vector("requests" -> requestResources))
  }

  private def toHalResource(saUtr: SaUtr, request: AgentClientAuthorisationRequest): HalResource = {
    val links = HalLinks(Vector(HalLink("self", s"${uk.gov.hmrc.agentclientauthorisation.controllers.routes.ClientAuthorisationRequestController.getRequests(saUtr).url}/${request.id}")))
    HalResource(links, Json.toJson(request).as[JsObject])
  }

}
