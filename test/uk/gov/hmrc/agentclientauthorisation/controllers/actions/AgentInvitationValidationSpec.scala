/*
 * Copyright 2021 HM Revenue & Customs
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

import org.scalatestplus.mockito.MockitoSugar
import play.api.mvc.{Result, Results}
import uk.gov.hmrc.agentclientauthorisation.model.AgentInvitation
import uk.gov.hmrc.agentclientauthorisation.support.AkkaMaterializerSpec
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgentInvitationValidationSpec extends UnitSpec with AgentInvitationValidation with Results with MockitoSugar with AkkaMaterializerSpec {

  private val validMtdItInvite: AgentInvitation = AgentInvitation("HMRC-MTD-IT", Some("personal"), "ni", "AA123456A")
  private val validMtdVatInvite: AgentInvitation = AgentInvitation("HMRC-MTD-VAT", Some("business"), "vrn", "101747641")

  private implicit class ResultChecker(r: Result) {
    def is(r1: Result) = {
      status(r1) shouldBe status(r)
      r
    }

    def withCode(code: String) = {
      (jsonBodyOf(r) \ "code").as[String] shouldBe code
      r
    }
  }

  private def responseFor(invite: AgentInvitation): Result =
    responseOptFor(invite).get

  private def responseOptFor(invite: AgentInvitation): Option[Result] =
    await(checkForErrors(invite))

  "checkForErrors" should {

    "fail with NotImplemented if the service is not HMRC-MTD-IT" in {
      responseFor(validMtdItInvite.copy(service = "some-other-service")) is NotImplemented
    }

    "only perform NINO format validation after establishing that clientId is a NINO" in {
      val result = responseFor(validMtdItInvite.copy(clientIdType = "not nino", clientId = "not a valid NINO"))
      result is BadRequest withCode "UNSUPPORTED_CLIENT_ID_TYPE"
    }

    "fail when VRN is invalid (i.e. does not match VRN regex) and service is of type HMRC-MTD-VAT" in {
      responseFor(validMtdVatInvite.copy(clientId = "12345678")) is BadRequest withCode "INVALID_CLIENT_ID"
      responseFor(validMtdVatInvite.copy(clientId = "1234567890")) is BadRequest withCode "INVALID_CLIENT_ID"
      responseFor(validMtdVatInvite.copy(clientId = "A01747641")) is BadRequest withCode "INVALID_CLIENT_ID"
    }

    "not fail due to VRN's checksum digits" in {
      val vrnGoodChecksum = "101747641"
      val vrnBadChecksum = "101747642"
      responseOptFor(validMtdVatInvite.copy(clientId = vrnGoodChecksum)) shouldBe None
      responseOptFor(validMtdVatInvite.copy(clientId = vrnBadChecksum)) shouldBe None
    }

    "fail when ni is supplied as client id type for service HMRC-MTD-VAT" in {
      responseFor(validMtdVatInvite.copy(clientIdType = "ni")) is BadRequest withCode "UNSUPPORTED_CLIENT_ID_TYPE"
    }
  }
}
