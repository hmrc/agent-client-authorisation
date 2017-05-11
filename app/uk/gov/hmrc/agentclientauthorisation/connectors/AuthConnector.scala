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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL
import javax.inject._

import com.kenshoo.play.metrics.Metrics
import play.api.libs.json.JsValue
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.model.{AuthEnrolment, Enrolments}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpReads}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class Authority(
      nino: Option[Nino],
      findArn: () => Future[Option[Arn]] = () => Future successful None
    )

@Singleton
class AuthConnector @Inject()(@Named("auth-baseUrl") baseUrl: URL, httpGet: HttpGet, metrics: Metrics) extends HttpAPIMonitor {
  override val kenshooRegistry = metrics.defaultRegistry

  def currentAuthority()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Authority] =
    httpGetAs[JsValue]("/auth/authority").map( responseJson =>
      Authority(
        nino =  (responseJson \ "nino").asOpt[Nino],
        findArn = () => monitor("ConsumedAPI-AUTH-GetEnrolments-GET") {enrolments(responseJson)}.map(_.arnOption)
      )
    )

  def currentArn()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Arn]] =
    currentAuthority.flatMap(_.findArn())

  private def enrolments(authorityJson: JsValue)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Enrolments] =
    httpGetAs[Set[AuthEnrolment]](enrolmentsRelativeUrl(authorityJson)).map(Enrolments(_))

  private def enrolmentsRelativeUrl(authorityJson: JsValue) = (authorityJson \ "enrolments").as[String]

  private def url(relativeUrl: String): URL = new URL(baseUrl, relativeUrl)

  private def httpGetAs[T](relativeUrl: String)(implicit rds: HttpReads[T], hc: HeaderCarrier, ec: ExecutionContext): Future[T] =
    httpGet.GET[T](url(relativeUrl).toString)(rds, hc)

}
