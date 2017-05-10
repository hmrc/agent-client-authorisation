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

package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.http.Status
import play.api.libs.json.Json
import play.api.mvc.Result
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.support.AkkaMaterializerSpec
import uk.gov.hmrc.play.test.UnitSpec

class ErrorResultsSpec extends UnitSpec with AkkaMaterializerSpec {

  "the constants" should {

    "contain the correct data" in {
      def checkConstant(constant: Result, status: Int, code: String, message: String) = {
        constant.header.status shouldBe status
        val jsonBody = jsonBodyOf(constant)
        (jsonBody \ "code").as[String] shouldBe code
        (jsonBody \ "message").as[String] shouldBe message
      }

      checkConstant(GenericUnauthorized, Status.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token is missing or not authorized.")
      checkConstant(AgentRegistrationNotFound, Status.FORBIDDEN, "AGENT_REGISTRATION_NOT_FOUND", "The Agent's MTDfB registration was not found.")
      checkConstant(ClientRegistrationNotFound, Status.FORBIDDEN, "CLIENT_REGISTRATION_NOT_FOUND", "The Client's MTDfB registration was not found.")
      checkConstant(SaEnrolmentNotFound, Status.FORBIDDEN, "SA_ENROLMENT_NOT_FOUND", "The Client must have an active IR-SA enrolment.")
      checkConstant(NotAnAgent, Status.FORBIDDEN, "NOT_AN_AGENT", "The logged in user is not an agent.")
      checkConstant(NoPermissionOnAgency, Status.FORBIDDEN, "NO_PERMISSION_ON_AGENCY", "The logged in user is not permitted to access invitations for the specified agency.")
      checkConstant(NoPermissionOnClient, Status.FORBIDDEN, "NO_PERMISSION_ON_CLIENT", "The logged in client is not permitted to access invitations for the specified client.")
      checkConstant(PostcodeDoesNotMatch, Status.FORBIDDEN, "POSTCODE_DOES_NOT_MATCH", "The submitted postcode did not match the client's postcode as held by HMRC.")
      checkConstant(InvitationNotFound, Status.NOT_FOUND, "INVITATION_NOT_FOUND", "The specified invitation was not found.")
      checkConstant(invalidInvitationStatus("My error message"), Status.FORBIDDEN, "INVALID_INVITATION_STATUS", "My error message")
      checkConstant(postcodeFormatInvalid("My error message"), Status.BAD_REQUEST, "POSTCODE_FORMAT_INVALID", "My error message")
      checkConstant(unsupportedService("My error message"), Status.NOT_IMPLEMENTED, "UNSUPPORTED_SERVICE", "My error message")
    }

  }

  "errorBodyWrites" should {

    "produce the correct JSON" in {

      val json = Json.toJson(ErrorBody("MY_ERROR_CODE", "My error message"))
      json.toString shouldBe """{"code":"MY_ERROR_CODE","message":"My error message"}"""
    }
  }
}
