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

package uk.gov.hmrc.agentclientauthorisation.scenarios

import org.joda.time.DateTime
import org.scalatest.concurrent.Eventually
import play.api.libs.json.{JsArray, JsObject, JsValue}
import play.mvc.Http.HeaderNames._
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.scenarios.EmbeddedSection.{EmbeddedInvitation, EmbeddedInvitationLinks}
import uk.gov.hmrc.agentclientauthorisation.scenarios.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.agentclientauthorisation.support.Resource
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.controllers.RestFormats
import uk.gov.hmrc.play.http.logging.SessionId
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import views.html.helper._

import scala.util.Try


object HalTestHelpers {
  def apply(json: JsValue) = new HalResourceHelper(json)

  class HalResourceHelper(json: JsValue) {
    def underlying: JsValue = json
    def embedded: EmbeddedSection = new EmbeddedSection(json \ "_embedded")
    def links: LinkSection = new LinkSection(json \ "_links")
    def numberOfInvitations = embedded.invitations.size
    def firstInvitation = embedded.invitations.head
    def secondInvitation = embedded.invitations(1)
  }
}

class AgencyApi(arn: Arn, port: Int) {

  private val getInvitationsUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
  private val getInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/"
  private val createInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(arn.arn)))

  def sendInvitation(clientId: MtdClientId, regime: String = "mtd-sa", postcode:String = "AA1 1AA"): String = {

    val response = new Resource(createInvitationUrl, port).postAsJson(
      s"""{"regime": "$regime", "clientId": "${clientId.value}", "postcode": "$postcode"}"""
    )(hc)

    require(response.status == 201, s"creating an invitation should return 201, was [${response.status}]")
    response.header(LOCATION).get
  }

  def sentInvitations(filteredBy:Seq[(String, String)] = Nil): HalResourceHelper = {

    val params = withFilterParams(filteredBy)
    val response: HttpResponse = new Resource(getInvitationsUrl+params, port).get()(hc)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }

  def withFilterParams(filteredBy: Seq[(String, String)]): String = {
    filteredBy match {
      case Nil => ""
      case (k, v) :: Nil => s"?$k=$v"
      case (k, v) :: tail => s"?$k=$v" + tail.map(params => s"&${params._1}=${params._2}")
    }
  }
}


class ClientApi(clientId: MtdClientId, port: Int) extends Eventually {

  private val getClientInvitationUrl = s"/agent-client-authorisation/clients/${urlEncode(clientId.value)}/invitations/received"
  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(clientId.value)))

  def acceptInvitation(invitation: EmbeddedInvitation): HttpResponse = {
    invitation.links.acceptLink.map { acceptLink =>
      val response: HttpResponse = new Resource(acceptLink, port).putEmpty()(hc)
      require(response.status == 204, s"response for accepting invitation should be 204, was [${response.status}]")
      response
    } .getOrElse (throw new IllegalStateException("Can't accept this invitation the accept link is not defined"))
  }

  def getInvitations(): HalResourceHelper = {
    val response: HttpResponse = new Resource(getClientInvitationUrl, port).get()(hc)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }
}


object EmbeddedSection {

  case class EmbeddedInvitationLinks(selfLink: String, agencyLink:Option[String], cancelLink: Option[String], acceptLink: Option[String], rejectLink: Option[String])
  case class EmbeddedInvitation(links: EmbeddedInvitationLinks, arn: Arn, regime: Regime, clientId: MtdClientId, status: String, created: DateTime, lastUpdated: DateTime)
}

class EmbeddedSection(embedded: JsValue) {

  def underlying: JsValue = embedded
  def isEmpty: Boolean = invitations isEmpty

  lazy val invitations: Seq[EmbeddedInvitation] = getInvitations.value.map(asInvitation)

  private def getInvitations: JsArray = {
    embedded \ "invitations" match {
      case array: JsArray => array
      case obj: JsObject => JsArray(Seq(obj))
    }
  }

  private def asInvitation(invitation: JsValue): EmbeddedInvitation = {

    implicit val dateReads = RestFormats.dateTimeRead

    def find(path: JsValue) = Try(path.as[String]) toOption
    def getString(path: JsValue) = path.as[String]
    def getDateTime(path: JsValue) = path.as[DateTime]

    EmbeddedInvitation(
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

  def underlying: JsValue = links

  def selfLink: String = (links \ "self" \ "href").as[String]

  def invitations: Seq[String] = (links \ "self" \ "invitations" \\ "href").map(_.as[String])
}

