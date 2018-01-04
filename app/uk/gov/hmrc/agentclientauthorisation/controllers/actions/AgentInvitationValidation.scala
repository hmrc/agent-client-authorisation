/*
 * Copyright 2018 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.model.{AgentInvitation, Invitation, NinoType, Service}
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.domain.Nino

import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.http.HeaderCarrier

trait AgentInvitationValidation extends Results {

  val postcodeService: PostcodeService

  private type Validation = (AgentInvitation) => Future[Option[Result]]

  private val postcodeWithoutSpacesRegex = "^[A-Za-z]{1,2}[0-9]{1,2}[A-Za-z]?[0-9][A-Za-z]{2}$".r

  val hasValidPostcode: Validation = (invite) => {
    Future successful (
      if (invite.clientPostcode.isEmpty) {
        if (invite.service == Service.MtdIt.id) Some(postcodeRequired(invite.service)) else None
      } else {
        postcodeWithoutSpacesRegex.findFirstIn(PostcodeService.normalise(invite.clientPostcode.get)).map(_ => None)
          .getOrElse(Some(postcodeFormatInvalid(s"""The submitted postcode, "${invite.clientPostcode.get}", does not match the expected format.""")))
      })
  }

  private def postCodeMatches(implicit hc: HeaderCarrier, ec: ExecutionContext): Validation = (invite) => {
    if (invite.clientPostcode.isEmpty) Future successful None
    else postcodeService.clientPostcodeMatches(invite.clientId, invite.clientPostcode.get)
  }

  private val hasValidNino: Validation = (invitation) => {
    if (Nino.isValid(invitation.clientId)) Future successful None
    else Future successful Some(InvalidNino)
  }

  private val supportedService: Validation  = (invite) => {
    if (Service.findById(invite.service).isDefined) Future successful None
    else Future successful Some(unsupportedService(s"""Unsupported service "${invite.service}""""))
  }

  private val supportedClientIdType: Validation = (invite) => {
    if(NinoType.id == invite.clientIdType) Future successful None
    else Future successful Some(unsupportedClientIdType(s"""Unsupported clientIdType "${invite.clientIdType}", the only currently supported type is "${NinoType.id}""""))
  }

  def checkForErrors(agentInvitation: AgentInvitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Result]] = {
    Seq(hasValidPostcode, supportedService, supportedClientIdType, hasValidNino, postCodeMatches)
      .foldLeft(Future.successful[Option[Result]](None))((acc, validation) => acc.flatMap {
      case None => validation(agentInvitation)
      case r => Future.successful(r)
    })
  }
}
