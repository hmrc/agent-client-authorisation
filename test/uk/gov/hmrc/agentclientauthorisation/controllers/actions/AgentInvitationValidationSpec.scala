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

import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentclientauthorisation.model.AgentInvite
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.play.test.UnitSpec

class AgentInvitationValidationSpec extends UnitSpec with AgentInvitationValidation with Results {

  override val postcodeService: PostcodeService = new PostcodeService

  private val validInvite: AgentInvite = AgentInvite("mtd-sa", "clientId", "AN11PA")

  private implicit class ResultChecker(r: Result) {
    def is(r1: Result) = status(r1) shouldBe status(r)
  }

  private def responseFor(invite: AgentInvite): Result = {
    checkForErrors(invite).head
  }


  "checkForErrors" should {


    "fail with Forbidden if the postcode doesn't start with A" in {
      responseFor(validInvite.copy(postcode = "BN29AB")) is Forbidden
    }

    "fail with BadRequest if the postcode is not valid" in {
      responseFor(validInvite.copy(postcode = "AAAAAA")) is BadRequest
    }

    "fail with NotImplemented if the regime is not mtd-sa" in {
      responseFor(validInvite.copy(regime = "mtd-vat")) is NotImplemented
    }

    "pass when the postcode is valid, begins with A and has mtd-sa as the regime" in {
      checkForErrors(validInvite) shouldBe Nil
    }
  }
}
