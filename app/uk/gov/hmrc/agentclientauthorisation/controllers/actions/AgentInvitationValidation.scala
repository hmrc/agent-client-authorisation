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

package uk.gov.hmrc.agentclientauthorisation.controllers.actions

import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.controllers.SUPPORTED_REGIME
import uk.gov.hmrc.agentclientauthorisation.model.AgentInvitation
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

trait AgentInvitationValidation extends Results {

  val postcodeService: PostcodeService

  private type Validation = (AgentInvitation) => Option[Result]

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r

  val hasValidPostcode: (AgentInvitation) => Future[Option[Result]] = (invite) => {
    Future successful postcodeWithoutSpacesRegex.findFirstIn(PostcodeService.normalise(invite.postcode)).map(_ => None)
      .getOrElse(Some(postcodeFormatInvalid(s"""The submitted postcode, "${invite.postcode}", does not match the expected format.""")))
  }

  private def postCodeMatches(implicit hc: HeaderCarrier, ec: ExecutionContext): (AgentInvitation) => Future[Option[Result]] = (invite) => {
    postcodeService.clientPostcodeMatches(invite.clientId, invite.postcode)
  }

  private val hasValidNino: Validation = (invitation) => {
    if (Nino.isValid(invitation.clientId)) None
    else Some(InvalidNino)
  }

  private val supportedRegime: (AgentInvitation) => Future[Option[Result]] = (invite) => {
    if(SUPPORTED_REGIME == invite.regime) Future successful None
    else Future successful Some(unsupportedRegime(s"""Unsupported regime "${invite.regime}", the only currently supported regime is "$SUPPORTED_REGIME""""))
  }

  def checkForErrors(agentInvitation: AgentInvitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Seq[Result]] = {
    val ninoValid = hasValidNino(agentInvitation)
    if (ninoValid.isEmpty) {
      val res = Seq(hasValidPostcode, postCodeMatches, supportedRegime).map(x => x(agentInvitation))
      Future.sequence(res).map(_.flatten)
    } else {
      Future successful Seq(ninoValid.get)
    }
  }
}
