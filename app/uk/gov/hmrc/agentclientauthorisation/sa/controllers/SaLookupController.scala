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

package uk.gov.hmrc.agentclientauthorisation.sa.controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import play.api.mvc.{Action, Result}
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.sa.services.SaLookupService
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}

class SaLookupController(authConnector: AuthConnector, saLookupService: SaLookupService)
  extends BaseController {

  def authoriseAndLookup(saUtr: SaUtr, postcode: String) = Action.async { implicit request =>
    // perhaps authorisation would be better implemented using action composition?
    authConnector.containsEnrolment("IR-SA-AGENT")(_.isActivated).flatMap { authorised =>
      if (authorised)
        lookup(saUtr, postcode)
      else
        Future successful Unauthorized
    }
  }

  private def lookup(saUtr: SaUtr, postcode: String)(implicit hc: HeaderCarrier): Future[Result] =
    saLookupService.lookupByUtrAndPostcode(saUtr, postcode).map {
      case Some(name) =>
        Ok(Json.obj("name" -> name))
      case None =>
        NotFound
    }
}
