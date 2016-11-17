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
import javax.inject._

import play.api.libs.json.JsValue
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class Accounts(agent: Option[AgentCode], sa: Option[SaUtr])

@Singleton
class AuthConnector @Inject() (@Named("auth-baseUrl") baseUrl: URL, httpGet: HttpGet) {

  def currentAccounts()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Accounts] =
    currentAuthority() map authorityAsAccounts

  private def currentAuthority()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[JsValue] =
    httpGetAs[JsValue]("/auth/authority")

  private def authorityAsAccounts(authority: JsValue): Accounts =
    Accounts(
      agent = (authority \ "accounts" \ "agent" \ "agentCode").asOpt[AgentCode],
      sa = (authority \ "accounts" \ "sa" \ "utr").asOpt[SaUtr]
    )

  private def url(relativeUrl: String): URL = new URL(baseUrl, relativeUrl)

  private def httpGetAs[T](relativeUrl: String)(implicit rds: HttpReads[T], hc: HeaderCarrier, ec: ExecutionContext): Future[T] =
    httpGet.GET[T](url(relativeUrl).toString)(rds, hc)

}
