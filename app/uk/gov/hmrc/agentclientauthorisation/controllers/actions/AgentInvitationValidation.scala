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

package uk.gov.hmrc.agentclientauthorisation.controllers.actions

import play.api.libs.json.Json
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentclientauthorisation.model.AgentInvite
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService

trait AgentInvitationValidation extends Results {

  val postcodeService: PostcodeService

  private type Validation = (AgentInvite) => Option[Result]

  private val SUPPORTED_REGIME = "mtd-sa"

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r

  val hasValidPostcode: (AgentInvite) => Option[Result] = (invite) => {
    postcodeWithoutSpacesRegex.findFirstIn(invite.postcode.replaceAll(" ", "")).map(_ => None)
      .getOrElse(Some(BadRequest))
  }

  private val postCodeMatches: (AgentInvite) => Option[Result] = (invite) => {
    if(postcodeService.clientPostcodeMatches(invite.clientId, invite.postcode)) None
    else Some(Forbidden)
  }

  private val supportedRegime: (AgentInvite) => Option[Result] = (invite) => {
    if(SUPPORTED_REGIME == invite.regime) None
    else {
      val responseBody = Json.obj(
        "code" -> "UNSUPPORTED_REGIME",
        "message" -> s"""Unsupported regime "${invite.regime}", the only currently supported regime is "$SUPPORTED_REGIME""""
      )
      Some(NotImplemented(responseBody))
    }
  }

  def checkForErrors(authRequest: AgentInvite): Seq[Result] =
    Seq(hasValidPostcode, postCodeMatches, supportedRegime).flatMap(_ (authRequest))
}
