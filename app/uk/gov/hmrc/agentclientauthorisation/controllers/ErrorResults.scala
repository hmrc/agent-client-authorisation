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

import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Results

object ErrorResults extends Results {

  class ErrorCode(val value: String) extends AnyVal

  case class ErrorBody(code: ErrorCode, message: String)

  object ErrorCodes {
    val AgentRegistrationNotFound = new ErrorCode("AGENT_REGISTRATION_NOT_FOUND")
    val ClientRegistrationNotFound = new ErrorCode("CLIENT_REGISTRATION_NOT_FOUND")

    val NotAnAgent = new ErrorCode("NOT_AN_AGENT")
    val GenericUnauthorized = new ErrorCode("UNAUTHORIZED")
    val SaEnrolmentNotFound = new ErrorCode("SA_ENROLMENT_NOT_FOUND")
  }

  implicit val errorBodyWrites = new Writes[ErrorBody] {
    override def writes(body: ErrorBody): JsValue = Json.obj("code" -> body.code.value, "message" -> body.message)
  }

  val GenericUnauthorizedResult        = Unauthorized(toJson(ErrorBody(ErrorCodes.GenericUnauthorized, "Bearer token is missing or not authorized")))
  val AgentRegistrationNotFoundResult  = Forbidden(toJson(ErrorBody(ErrorCodes.AgentRegistrationNotFound, "The Agent's MTDfB registration was not found")))
  val ClientRegistrationNotFoundResult = Forbidden(toJson(ErrorBody(ErrorCodes.ClientRegistrationNotFound, "The Client's MTDfB registration was not found")))
  val SaEnrolmentNotFoundResult        = Forbidden(toJson(ErrorBody(ErrorCodes.SaEnrolmentNotFound, "The Client must have an active IR-SA enrolment")))
  val NotAnAgentResult                 = Forbidden(toJson(ErrorBody(ErrorCodes.NotAnAgent, "The logged in user is not an agent")))
}
