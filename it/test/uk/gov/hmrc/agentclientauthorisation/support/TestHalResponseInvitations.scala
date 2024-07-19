/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.support
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.model.DetailsForEmail
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId

case class TestHalResponseInvitations(invitations: List[TestHalResponseInvitation])

object TestHalResponseInvitations {
  implicit val format: OFormat[TestHalResponseInvitations] = Json.format[TestHalResponseInvitations]
}

case class TestHalResponseInvitation(
  href: String,
  invitationId: InvitationId,
  arn: String,
  service: String,
  clientType: String,
  clientIdType: String,
  clientId: String,
  suppliedClientIdType: String,
  suppliedClientId: String,
  status: String,
  detailsForEmail: Option[DetailsForEmail],
  clientActionUrl: Option[String],
  created: String,
  expiryDate: String,
  lastUpdated: String
) {}

object TestHalResponseInvitation {

  implicit val reads: Reads[TestHalResponseInvitation] =
    ((JsPath \ "_links" \ "self" \ "href").read[String] and
      (JsPath \ "invitationId").read[String] and
      (JsPath \ "arn").read[String] and
      (JsPath \ "service").read[String] and
      (JsPath \ "clientType").read[String] and
      (JsPath \ "clientIdType").read[String] and
      (JsPath \ "clientId").read[String].map(_.replaceAll(" ", "")) and
      (JsPath \ "suppliedClientIdType").read[String] and
      (JsPath \ "suppliedClientId").read[String].map(_.replaceAll(" ", "")) and
      (JsPath \ "status").read[String] and
      (JsPath \ "detailsForEmail").readNullable[DetailsForEmail] and
      (JsPath \ "clientActionUrl").readNullable[String] and
      (JsPath \ "created").read[String] and
      (JsPath \ "expiryDate").read[String] and
      (JsPath \ "lastUpdated").read[String])(
      (
        href,
        invitationId,
        arn,
        service,
        clientType,
        clientIdType,
        clientId,
        suppliedClientIdType,
        suppliedClientId,
        status,
        detailsForEmail,
        clientActionUrl,
        created,
        expiryDate,
        lastUpdated
      ) =>
        TestHalResponseInvitation(
          href,
          InvitationId(invitationId),
          arn,
          service,
          clientType,
          clientIdType,
          clientId,
          suppliedClientIdType,
          suppliedClientId,
          status,
          detailsForEmail,
          clientActionUrl,
          created,
          expiryDate,
          lastUpdated
        )
    )

  implicit val writes: Writes[TestHalResponseInvitation] = new Writes[TestHalResponseInvitation] {
    override def writes(o: TestHalResponseInvitation): JsValue = Json.obj(
      "_links"               -> Json.obj("self" -> Json.obj("href" -> o.href)),
      "invitationId"         -> o.invitationId.value,
      "arn"                  -> o.arn,
      "service"              -> o.service,
      "clientType"           -> o.clientType,
      "clientIdType"         -> o.clientIdType,
      "clientId"             -> o.clientId.replaceAll(" ", ""),
      "suppliedClientIdType" -> o.suppliedClientIdType,
      "suppliedClientIdType" -> o.suppliedClientId.replaceAll(" ", ""),
      "status"               -> o.status,
      "detailsForEmail"      -> o.detailsForEmail,
      "clientActionUrl"      -> o.clientActionUrl,
      "created"              -> o.created,
      "expiryDate"           -> o.expiryDate,
      "lastUpdated"          -> o.lastUpdated
    )
  }
}
