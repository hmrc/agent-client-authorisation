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

import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.domain.{AgentCode, SimpleObjectReads, SimpleObjectWrites}
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.{ExecutionContext, Future}

case class Agency(arn: Arn)
object Agency {
  implicit val arnReads = new SimpleObjectReads[Arn]("arn", Arn.apply)
  implicit val arnWrites = new SimpleObjectWrites[Arn](_.arn)
  implicit val jsonFormats = Json.format[Agency]
}


class AgenciesFakeConnector(baseUrl: URL, httpGet: HttpGet) {

  def findArn(agentCode: AgentCode)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Arn]] = {
    httpGet.GET[Option[Agency]](new URL(baseUrl, s"/agencies-fake/agencies/agentcode/${encodePathSegment(agentCode.value)}").toString)
      .map(_.map(_.arn))
  }

  def agencyUrl(arn: Arn): URL = {
    new URL(baseUrl, s"/agencies-fake/agencies/${encodePathSegment(arn.arn)}")
  }

}
