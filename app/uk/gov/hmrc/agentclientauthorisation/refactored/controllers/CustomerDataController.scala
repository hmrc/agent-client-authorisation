/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.refactored.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.agentclientauthorisation.refactored.services.CustomerDataService
import uk.gov.hmrc.agentservice.{CustomerDataCheckRequest, CustomerDataCheckResponse, CustomerDataCheckSuccess, CustomerDataCheckUnsuccessful}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Provider, Singleton}
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}

@Singleton
class CustomerDataController @Inject()(cc: ControllerComponents, cds: CustomerDataService, ecp: Provider[ExecutionContextExecutor])
    extends BackendController(cc) with Logging {

  implicit val ec: ExecutionContext = ecp.get

  def customerDataCheck(service: String, clientId: String): Action[JsValue] = Action(parse.json).async { implicit request =>
    val mKnownFact = request.body.as[CustomerDataCheckRequest].knownFact
    cds
      .customerDataCheck(service, clientId, mKnownFact)
      .map {
        case a: CustomerDataCheckResponse if a.hodResponseCode == 503 =>
          logger.warn(s"service: $service, clientId: ***${clientId.drop(4)}, status: ${a.hodResponseCode}, message: ${a.message}")
          InternalServerError(Json.toJson(a))
        //case a: CustomerDataCheckUnsuccessful => Forbidden(Json.toJson(a))
        case a: CustomerDataCheckSuccess => Ok(Json.toJson(a))
      }
  }

}
