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
import uk.gov.hmrc.agentclientauthorisation.CLIENT_ID_TYPE_NINO
import uk.gov.hmrc.agentclientauthorisation.model.{AgentInvitation, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

trait AgentInvitationValidation extends Results {

  val postcodeService: PostcodeService

  private type Validation = (AgentInvitation) => Future[Option[Result]]

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r

  val hasValidPostcode: (AgentInvitation) => Future[Option[Result]] = (invite) => {
    Future successful postcodeWithoutSpacesRegex.findFirstIn(PostcodeService.normalise(invite.clientPostcode)).map(_ => None)
      .getOrElse(Some(postcodeFormatInvalid(s"""The submitted postcode, "${invite.clientPostcode}", does not match the expected format.""")))
  }

  private def postCodeMatches(implicit hc: HeaderCarrier, ec: ExecutionContext): (AgentInvitation) => Future[Option[Result]] = (invite) => {
    postcodeService.clientPostcodeMatches(invite.clientId, invite.clientPostcode)
  }

  private val hasValidNino: Validation = (invitation) => {
    if (Nino.isValid(invitation.clientId)) Future successful None
    else Future successful Some(InvalidNino)
  }

  private val supportedService: (AgentInvitation) => Future[Option[Result]] = (invite) => {
    if (Service.valueOfId(invite.service).isDefined) Future successful None
    else Future successful Some(unsupportedService(s"""Unsupported service "${invite.service}""""))
  }

  private val supportedClientIdType: (AgentInvitation) => Future[Option[Result]] = (invite) => {
    if(CLIENT_ID_TYPE_NINO == invite.clientIdType) Future successful None
    else Future successful Some(unsupportedClientIdType(s"""Unsupported clientIdType "${invite.clientIdType}", the only currently supported type is "$CLIENT_ID_TYPE_NINO""""))
  }

  def checkForErrors(agentInvitation: AgentInvitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Result]] = {
    Seq(hasValidPostcode, supportedService, supportedClientIdType, hasValidNino, postCodeMatches)
      .foldLeft(Future.successful[Option[Result]](None))((acc, validation) => acc.flatMap {
      case None => validation(agentInvitation)
      case r => Future.successful(r)
    })
  }
}
