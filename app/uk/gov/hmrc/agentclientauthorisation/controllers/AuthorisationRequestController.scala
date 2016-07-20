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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.Action
import uk.gov.hmrc.agentclientauthorisation.model.AuthorisationRequestRequest
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class AuthorisationRequestController(authorisationRequestRepository: AuthorisationRequestRepository) extends BaseController {

  def createRequest(agentCode: AgentCode) = Action.async(parse.json) { implicit request =>
    withJsonBody[AuthorisationRequestRequest] { authRequest =>
      // TODO Audit
      authorisationRequestRepository.create(agentCode, authRequest.clientSaUtr)
      Future successful Created //TODO Location header?
    }
  }

  def getRequests(agentCode: AgentCode) = Action.async { implicit request =>
    authorisationRequestRepository.list(agentCode).map(Json.toJson(_)).map(Ok(_))
  }

}
