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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL

import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.domain.{SaUtr, AgentCode}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}

import scala.concurrent.{ExecutionContext, Future}

private[connectors] case class AuthEnrolment(key: String, state: String) {
  val isActivated: Boolean = state equalsIgnoreCase "Activated"
}

private[connectors] object AuthEnrolment {
  implicit val format = Json.format[AuthEnrolment]
}

private[connectors] case class Enrolments(enrolments: Set[AuthEnrolment]) {
  def activatedSaEnrolment: Option[AuthEnrolment] = getActivatedEnrolment("IR-SA-AGENT")

  private def getActivatedEnrolment(key: String): Option[AuthEnrolment] = enrolments.find(e => e.key == key && e.isActivated)
}

private[connectors] object Enrolments {
  implicit val formats = Json.format[Enrolments]
}

case class Accounts(agent: Option[AgentCode], sa: Option[SaUtr])
case class UserInfo(accounts: Accounts, hasActivatedIrSaEnrolment: Boolean)

class AuthConnector(baseUrl: URL, httpGet: HttpGet) {

  def hasActivatedIrSaEnrolment()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    currentAuthority
      .flatMap(enrolments)
      .map(_.activatedSaEnrolment.isDefined)

  def currentAccounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] =
    currentAuthority() map authorityAsAccounts

  def currentUserInfo()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[UserInfo] = {
    for {
      authority <- currentAuthority()
      enrolments <- enrolments(authority)
    } yield {
      UserInfo(
        accounts = authorityAsAccounts(authority),
        hasActivatedIrSaEnrolment = enrolments.activatedSaEnrolment.isDefined
      )
    }
  }

  private def currentAuthority()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    httpGetAs[JsValue]("/auth/authority")

  private def authorityAsAccounts(authority: JsValue): Accounts =
    Accounts(
      agent = (authority \ "accounts" \ "agent" \ "agentCode").asOpt[AgentCode],
      sa = (authority \ "accounts" \ "sa" \ "utr").asOpt[SaUtr]
    )

  private def enrolments(authorityJson: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Enrolments] =
    httpGetAs[Set[AuthEnrolment]](enrolmentsRelativeUrl(authorityJson)).map(Enrolments(_))

  private def enrolmentsRelativeUrl(authorityJson: JsValue) = (authorityJson \ "enrolments").as[String]

  private def url(relativeUrl: String): URL = new URL(baseUrl, relativeUrl)

  private def httpGetAs[T](relativeUrl: String)(implicit rds: HttpReads[T], hc: HeaderCarrier, ec: ExecutionContext): Future[T] =
    httpGet.GET[T](url(relativeUrl).toString)(rds, hc)

}
