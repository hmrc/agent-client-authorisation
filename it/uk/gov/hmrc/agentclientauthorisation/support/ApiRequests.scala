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

import java.time.LocalDate

import play.api.libs.json.{JsObject, JsString, Json}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

trait ApiRequests {

  val sandboxMode: Boolean = false
  val apiPlatform: Boolean = true

  def baseUrl =
    if (sandboxMode) {
      "/sandbox"
    } else {
      if (apiPlatform) ""
      else "/agent-client-authorisation"
    }

  def externalUrl(serviceRouteUrl: String) =
    if (sandboxMode) {
      "/agent-client-authorisation" + stripPrefix(serviceRouteUrl, "/sandbox")
    } else {
      if (apiPlatform) "/agent-client-authorisation" + serviceRouteUrl
      else serviceRouteUrl
    }

  private def stripPrefix(s: String, prefix: String): String = {
    if (!s.startsWith(prefix)) throw new IllegalArgumentException(s""""$s\" does not start with $prefix"""")
    s.substring(prefix.length)
  }

  def agenciesUrl = s"$baseUrl/agencies"
  def agencyUrl(arn: Arn) = s"$agenciesUrl/${arn.value}"
  def agencyInvitationsUrl(arn: Arn) = s"${agencyUrl(arn)}/invitations"
  def agencyGetInvitationsUrl(arn: Arn): String = s"${agencyInvitationsUrl(arn)}/sent"
  def agencyGetInvitationUrl(arn: Arn, invitationId: String): String =
    s"$baseUrl/agencies/${arn.value}/invitations/sent/$invitationId"
  def agencyGetCheckKnownFactVat(vrn: Vrn, suppliedDate: LocalDate): String =
    s"$baseUrl/agencies/check-vat-known-fact/${vrn.value}/registration-date/${suppliedDate.toString}"
  def agencyGetCheckKnownFactItsa(nino: Nino, postcode: String): String =
    s"$baseUrl/agencies/check-sa-known-fact/${nino.value}/postcode/$postcode"

  def rootResource()(implicit port: Int) =
    new Resource(baseUrl, port).get()

  def agenciesResource()(implicit port: Int) =
    new Resource(agenciesUrl, port).get()

  def agencyResource(arn: Arn)(implicit port: Int) =
    new Resource(agencyUrl(arn), port).get()

  def agencyGetSentInvitations(arn: Arn, filteredBy: Seq[(String, String)] = Nil)(
    implicit port: Int,
    hc: HeaderCarrier): HttpResponse = {
    val params = withFilterParams(filteredBy)
    new Resource(agencyGetInvitationsUrl(arn) + params, port).get()(hc)
  }

  def agencyGetSentInvitation(arn: Arn, invitationId: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse =
    new Resource(agencyGetInvitationUrl(arn, invitationId), port).get()(hc)

  case class AgencyInvitationRequest(
    service: String,
    clientIdType: String,
    clientId: String,
    clientPostcode: Option[String]) {
    val jsObj: JsObject = {
      val obj = Json.obj("service" -> service, "clientIdType" -> clientIdType, "clientId" -> clientId)
      if (clientPostcode.isDefined) obj + ("clientPostcode" -> JsString(clientPostcode.get)) else obj
    }
    val json: String = Json.stringify(jsObj)
  }

  def agencySendInvitation(arn: Arn, invitation: AgencyInvitationRequest)(
    implicit port: Int,
    hc: HeaderCarrier): HttpResponse = {
    val url: String = agencyGetInvitationsUrl(arn)
    new Resource(url, port).postAsJson(invitation.json)
  }

  def agencyCancelInvitation(arn: Arn, invitationId: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse = {
    val url: String = agencyGetInvitationUrl(arn, invitationId) + "/cancel"
    new Resource(url, port).putEmpty()
  }

  def agentGetCheckVatKnownFact(vrn: Vrn, suppliedDate: LocalDate)(
    implicit port: Int,
    hc: HeaderCarrier): HttpResponse =
    new Resource(agencyGetCheckKnownFactVat(vrn, suppliedDate), port).get()(hc)

  def agentGetCheckItsaKnownFact(nino: Nino, postcode: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse =
    new Resource(agencyGetCheckKnownFactItsa(nino, postcode), port).get()(hc)

  /*
  CLIENT RELATED RESOURCES
  TODO:  split these into seperate traits?
   */
  def clientsUrl = s"$baseUrl/clients"

  def clientUrl(clientId: ClientId) = clientId match {
    case ClientIdentifier(MtdItId(value)) => s"$baseUrl/clients/MTDITID/$value"
    case ClientIdentifier(Nino(value))    => s"$baseUrl/clients/NI/$value"
    case ClientIdentifier(Vrn(value))     => s"$baseUrl/clients/VRN/$value"
  }

  def clientReceivedInvitationsUrl(clientId: ClientId) = s"${clientUrl(clientId)}/invitations/received"

  def clientReceivedInvitationUrl(clientId: ClientId, invitationId: String): String =
    s"${clientUrl(clientId)}/invitations/received/$invitationId"

  def clientsResource()(implicit port: Int) =
    new Resource(clientsUrl, port).get

  def clientResource(mtdItId: MtdItId)(implicit port: Int) =
    new Resource(clientUrl(mtdItId), port).get
  def clientGetReceivedInvitations(clientId: ClientId, filteredBy: Seq[(String, String)] = Nil)(
    implicit port: Int,
    hc: HeaderCarrier): HttpResponse = {
    val params = withFilterParams(filteredBy)
    new Resource(clientReceivedInvitationsUrl(clientId) + params, port).get()(hc)
  }

  def clientGetReceivedInvitation(mtdItId: MtdItId, invitationId: String)(
    implicit port: Int,
    hc: HeaderCarrier): HttpResponse =
    getReceivedInvitationResource(clientReceivedInvitationUrl(mtdItId, invitationId))

  def getReceivedInvitationResource(link: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse =
    new Resource(link, port).get()(hc)

  def clientAcceptInvitation(mtdItId: MtdItId, invitationId: String)(implicit port: Int, hc: HeaderCarrier) =
    updateInvitationResource(clientReceivedInvitationsUrl(mtdItId) + s"/$invitationId/accept")

  def clientRejectInvitation(mtdItId: MtdItId, invitationId: String)(implicit port: Int, hc: HeaderCarrier) =
    updateInvitationResource(clientReceivedInvitationsUrl(mtdItId) + s"/$invitationId/reject")

  def updateInvitationResource(link: String)(implicit port: Int, hc: HeaderCarrier): HttpResponse =
    new Resource(link, port).putEmpty()(hc)

  def withFilterParams(filteredBy: Seq[(String, String)]): String =
    filteredBy match {
      case Nil            => ""
      case (k, v) :: Nil  => s"?$k=$v"
      case (k, v) :: tail => s"?$k=$v" + tail.map(params => s"&${params._1}=${params._2}").mkString
    }
}
