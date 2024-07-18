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

package uk.gov.hmrc.agentclientauthorisation.support

import play.api.libs.json.{JsArray, JsLookupResult, JsObject, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.MongoLocalDateTimeFormat
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.{EmbeddedInvitation, EmbeddedInvitationLinks}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

import java.time.LocalDateTime
import scala.language.postfixOps

object HalTestHelpers {
  def apply(json: JsValue) = new HalResourceHelper(json)

  class HalResourceHelper(json: JsValue) {
    def embedded: EmbeddedSection = new EmbeddedSection((json \ "_embedded").as[JsValue])
    def links: LinkSection = new LinkSection((json \ "_links").as[JsValue])
    def numberOfInvitations = embedded.invitations.size
    def firstInvitation: EmbeddedInvitation = embedded.invitations.head
    def secondInvitation = embedded.invitations(1)
  }
}

object EmbeddedSection {

  case class EmbeddedInvitationLinks(selfLink: String, cancelLink: Option[String], acceptLink: Option[String], rejectLink: Option[String])
  case class EmbeddedInvitation(
    underlying: JsValue,
    links: EmbeddedInvitationLinks,
    arn: Arn,
    service: String,
    clientIdType: String,
    clientId: String,
    status: String,
    created: LocalDateTime,
    lastUpdated: LocalDateTime
  )
}

class EmbeddedSection(embedded: JsValue) {

  def isEmpty: Boolean = invitations isEmpty

  lazy val invitations: Seq[EmbeddedInvitation] = getInvitations.value.map(asInvitation).toSeq

  private def getInvitations: JsArray =
    (embedded \ "invitations").get match {
      case array: JsArray => array
      case obj: JsObject  => JsArray(Seq(obj))
      case _              => JsArray(Seq.empty)
    }

  private def asInvitation(invitation: JsValue): EmbeddedInvitation = {

    implicit val dateTimeReads = MongoLocalDateTimeFormat.localDateTimeFormat

    def find(path: JsLookupResult) = path.asOpt[String]
    def getString(path: JsLookupResult) = path.as[String]
    def getDateTime(path: JsLookupResult) = path.as[LocalDateTime]

    EmbeddedInvitation(
      underlying = invitation,
      EmbeddedInvitationLinks(
        getString(invitation \ "_links" \ "self" \ "href"),
        find(invitation \ "_links" \ "cancel" \ "href"),
        find(invitation \ "_links" \ "accept" \ "href"),
        find(invitation \ "_links" \ "reject" \ "href")
      ),
      arn = Arn(getString(invitation \ "arn")),
      service = getString(invitation \ "service"),
      clientIdType = getString(invitation \ "clientIdType"),
      clientId = getString(invitation \ "clientId"),
      status = getString(invitation \ "status"),
      created = getDateTime(invitation \ "created"),
      lastUpdated = getDateTime(invitation \ "lastUpdated")
    )
  }
}

class LinkSection(links: JsValue) {

  def selfLink: String = (links \ "self" \ "href").as[String]

  def invitations: Seq[String] = (links \ "invitations" \\ "href").map(_.as[String]).toSeq
}
