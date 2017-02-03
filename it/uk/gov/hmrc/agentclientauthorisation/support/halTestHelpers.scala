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

package uk.gov.hmrc.agentclientauthorisation.support

import org.joda.time.DateTime
import play.api.libs.json.{JsArray, JsLookupResult, JsObject, JsValue}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.{EmbeddedInvitation, EmbeddedInvitationLinks}
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.controllers.RestFormats

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

  case class EmbeddedInvitationLinks(selfLink: String, agencyLink:Option[String], cancelLink: Option[String], acceptLink: Option[String], rejectLink: Option[String])
  case class EmbeddedInvitation(underlying:JsValue, links: EmbeddedInvitationLinks, arn: Arn, regime: Regime, clientId: MtdClientId, status: String, created: DateTime, lastUpdated: DateTime)
}

class EmbeddedSection(embedded: JsValue) {

  def isEmpty: Boolean = invitations isEmpty

  lazy val invitations: Seq[EmbeddedInvitation] = getInvitations.value.map(asInvitation)

  private def getInvitations: JsArray = {
    (embedded \ "invitations").get match {
      case array: JsArray => array
      case obj: JsObject => JsArray(Seq(obj))
    }
  }

  private def asInvitation(invitation: JsValue): EmbeddedInvitation = {

    implicit val dateReads = RestFormats.dateTimeRead

    def find(path: JsLookupResult) = path.asOpt[String]
    def getString(path: JsLookupResult) = path.as[String]
    def getDateTime(path: JsLookupResult) = path.as[DateTime]

    EmbeddedInvitation(
      underlying = invitation,
      EmbeddedInvitationLinks(
        getString(invitation \ "_links" \ "self" \ "href"),
        find(invitation \ "_links" \ "agency" \ "href"),
        find(invitation \ "_links" \ "cancel" \ "href"),
        find(invitation \ "_links" \ "accept" \ "href"),
        find(invitation \ "_links" \ "reject" \ "href")
      ),
      arn = Arn(getString(invitation \ "arn")),
      regime = Regime(getString(invitation \ "regime")),
      clientId = MtdClientId(getString(invitation \ "clientId")),
      status = getString(invitation \ "status"),
      created = getDateTime(invitation \ "created"),
      lastUpdated = getDateTime(invitation \ "lastUpdated")
    )
  }
}

class LinkSection(links: JsValue) {

  def selfLink: String = (links \ "self" \ "href").as[String]

  def invitations: Seq[String] = (links \ "invitations" \\ "href").map(_.as[String])
}
