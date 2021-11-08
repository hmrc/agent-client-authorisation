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

package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.libs.json.Json.toJson
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Result
import play.api.mvc.Results._

object ErrorResults {

  case class ErrorBody(code: String, message: String)

  implicit val errorBodyWrites: Writes[ErrorBody] = new Writes[ErrorBody] {
    override def writes(body: ErrorBody): JsValue = Json.obj("code" -> body.code, "message" -> body.message)
  }

  val GenericUnauthorized: Result = Unauthorized(toJson(ErrorBody("UNAUTHORIZED", "Bearer token is missing or not authorized.")))
  val AgentNotSubscribed: Result = Forbidden(toJson(ErrorBody("AGENT_NOT_SUBSCRIBED", "The Agent is not subscribed to Agent Services.")))
  val ClientRegistrationNotFound: Result = Forbidden(
    toJson(ErrorBody("CLIENT_REGISTRATION_NOT_FOUND", "The Client's MTDfB registration or SAUTR (if alt-itsa is enabled) was not found.")))
  val NotAClient: Result = Forbidden(toJson(ErrorBody("NOT_A_CLIENT", "The logged in user must be a client to proceed.")))
  val NoPermissionOnAgency: Result = Forbidden(
    toJson(ErrorBody("NO_PERMISSION_ON_AGENCY", "The logged in user is not permitted to access invitations for the specified agency.")))
  val NoPermissionOnClient: Result = Forbidden(
    toJson(ErrorBody("NO_PERMISSION_ON_CLIENT", "The logged in client is not permitted to access invitations for the specified client.")))
  val PostcodeDoesNotMatch: Result = Forbidden(
    toJson(ErrorBody("POSTCODE_DOES_NOT_MATCH", "The submitted postcode did not match the client's postcode as held by HMRC.")))
  val DateOfBirthDoesNotMatch: Result = Forbidden(
    toJson(ErrorBody("DATE_OF_BIRTH_DOES_NOT_MATCH", "The submitted date of birth did not match the client's date of birth as held by HMRC.")))
  val PptRegistrationDateDoesNotMatch: Result = Forbidden(
    toJson(
      ErrorBody(
        "PPT_REGISTRATION_DATE_DOES_NOT_MATCH",
        "The submitted date of application does not match the date of application on the PPT Subscription Display record."
      ))
  )
  val PptCustomerDeregistered: Result = Forbidden(
    toJson(ErrorBody("PPT_CUSTOMER_IS_DEREGISTERED", "The PPT Subscription Display record is deregistered."))
  )
  val VatRegistrationDateDoesNotMatch: Result = Forbidden(
    toJson(
      ErrorBody(
        "VAT_REGISTRATION_DATE_DOES_NOT_MATCH",
        "The submitted VAT registration date did not match the client's VAT registration date as held by HMRC.")))
  val InvitationNotFound: Result = NotFound(toJson(ErrorBody("INVITATION_NOT_FOUND", "The specified invitation was not found.")))
  val PptSubscriptionNotFound: Result = NotFound(toJson(ErrorBody("PPT_SUBSCRIPTION_NOT_FOUND", "the EtmpRegistrationNumber was not found")))
  val InvalidClientId: Result = BadRequest(toJson(ErrorBody("INVALID_CLIENT_ID", "The CLIENT_ID specified is not in a valid format")))
  def nonUkAddress(countryCode: String): Result =
    NotImplemented(
      toJson(
        ErrorBody(
          "NON_UK_ADDRESS",
          s"This API does not currently support non-UK addresses. The client's country code should be 'GB' but it was '$countryCode'.")))
  def invalidInvitationStatus(message: String): Result =
    Forbidden(toJson(ErrorBody("INVALID_INVITATION_STATUS", message)))
  def unsupportedService(message: String): Result = NotImplemented(toJson(ErrorBody("UNSUPPORTED_SERVICE", message)))
  def unsupportedClientIdType(message: String): Result =
    BadRequest(toJson(ErrorBody("UNSUPPORTED_CLIENT_ID_TYPE", message)))
  def postcodeFormatInvalid(message: String): Result = BadRequest(toJson(ErrorBody("POSTCODE_FORMAT_INVALID", message)))
  def postcodeRequired(service: String): Result =
    BadRequest(toJson(ErrorBody("POSTCODE_REQUIRED", s"Postcode is required for service $service")))

  val GenericForbidden: Result = Forbidden(toJson(ErrorBody("NO_PERMISSION", "The logged in user is not permitted to perform the operation.")))

  def genericBadRequest(message: String): Result = BadRequest(toJson(ErrorBody("BAD_REQUEST", message)))

  def genericInternalServerError(message: String): Result =
    InternalServerError(toJson(ErrorBody("INTERNAL_SERVER_ERROR", message)))
}
