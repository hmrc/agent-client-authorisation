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

import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Results._

object ErrorResults {

  case class ErrorBody(code: String, message: String)

  implicit val errorBodyWrites = new Writes[ErrorBody] {
    override def writes(body: ErrorBody): JsValue = Json.obj("code" -> body.code, "message" -> body.message)
  }

  val GenericUnauthorized                       = Unauthorized(toJson(ErrorBody("UNAUTHORIZED", "Bearer token is missing or not authorized.")))
  val AgentRegistrationNotFound                 = Forbidden(toJson(ErrorBody("AGENT_REGISTRATION_NOT_FOUND", "The Agent's MTDfB registration was not found.")))
  val ClientRegistrationNotFound                = Forbidden(toJson(ErrorBody("CLIENT_REGISTRATION_NOT_FOUND", "The Client's MTDfB registration was not found.")))
  val SaEnrolmentNotFound                       = Forbidden(toJson(ErrorBody("SA_ENROLMENT_NOT_FOUND", "The Client must have an active IR-SA enrolment.")))
  val NotAnAgent                                = Forbidden(toJson(ErrorBody("NOT_AN_AGENT", "The logged in user is not an agent.")))
  val NoPermissionOnAgency                      = Forbidden(toJson(ErrorBody("NO_PERMISSION_ON_AGENCY", "The logged in user is not permitted to access invitations for the specified agency.")))
  val NoPermissionOnClient                      = Forbidden(toJson(ErrorBody("NO_PERMISSION_ON_CLIENT", "The logged in client is not permitted to access invitations for the specified client.")))
  val PostcodeDoesNotMatch                      = Forbidden(toJson(ErrorBody("POSTCODE_DOES_NOT_MATCH", "The submitted postcode did not match the client's postcode as held by HMRC.")))
  val InvitationNotFound                        = NotFound(toJson(ErrorBody("INVITATION_NOT_FOUND", "The specified invitation was not found.")))
  def invalidInvitationStatus(message: String)  = Forbidden(toJson(ErrorBody("INVALID_INVITATION_STATUS", message)))
  def unsupportedRegime(message: String)        = NotImplemented(toJson(ErrorBody("UNSUPPORTED_REGIME", message)))
  def postcodeFormatInvalid(message: String)    = BadRequest(toJson(ErrorBody("POSTCODE_FORMAT_INVALID", message)))

}
