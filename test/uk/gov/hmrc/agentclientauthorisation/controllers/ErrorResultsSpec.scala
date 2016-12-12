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

import play.api.http.Status
import play.api.mvc.Result
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.support.AkkaMaterializerSpec
import uk.gov.hmrc.play.test.UnitSpec

class ErrorResultsSpec extends UnitSpec with AkkaMaterializerSpec {

  "ErrorResults" should {

    "create correct data for the constants" in {
      def checkErrorResponse(constant: Result, status: Int, code: String, message: String) = {
        constant.header.status shouldBe status
        val jsonBody = jsonBodyOf(constant)
        (jsonBody \ "code").as[String] shouldBe code
        (jsonBody \ "message").as[String] shouldBe message
      }

      checkErrorResponse(GenericUnauthorizedResult, Status.UNAUTHORIZED, "UNAUTHORIZED", "Bearer token is missing or not authorized")
      checkErrorResponse(AgentRegistrationNotFoundResult, Status.FORBIDDEN, "AGENT_REGISTRATION_NOT_FOUND", "The Agent's MTDfB registration was not found")
      checkErrorResponse(ClientRegistrationNotFoundResult, Status.FORBIDDEN, "CLIENT_REGISTRATION_NOT_FOUND", "The Client's MTDfB registration was not found")
      checkErrorResponse(SaEnrolmentNotFoundResult, Status.FORBIDDEN, "SA_ENROLMENT_NOT_FOUND", "The Client must have an active IR-SA enrolment")
      checkErrorResponse(NotAnAgentResult, Status.FORBIDDEN, "NOT_AN_AGENT", "The logged in user is not an agent")
    }


    "create correct Result body" in {

      bodyOf(GenericUnauthorizedResult) shouldBe """{"code":"UNAUTHORIZED","message":"Bearer token is missing or not authorized"}"""
      bodyOf(AgentRegistrationNotFoundResult) shouldBe """{"code":"AGENT_REGISTRATION_NOT_FOUND","message":"The Agent's MTDfB registration was not found"}"""
      bodyOf(ClientRegistrationNotFoundResult) shouldBe """{"code":"CLIENT_REGISTRATION_NOT_FOUND","message":"The Client's MTDfB registration was not found"}"""
      bodyOf(SaEnrolmentNotFoundResult) shouldBe """{"code":"SA_ENROLMENT_NOT_FOUND","message":"The Client must have an active IR-SA enrolment"}"""
      bodyOf(NotAnAgentResult) shouldBe """{"code":"NOT_AN_AGENT","message":"The logged in user is not an agent"}"""
    }
  }
}
