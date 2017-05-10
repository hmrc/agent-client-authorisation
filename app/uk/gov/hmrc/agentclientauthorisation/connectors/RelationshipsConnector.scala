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

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpPut, HttpResponse}

import scala.concurrent.Future

@Singleton
class RelationshipsConnector @Inject() (@Named("relationships-baseUrl") baseUrl: URL, httpPut: HttpPut) {

  def createRelationship(arn: Arn, nino: Nino)(implicit hc: HeaderCarrier): Future[Unit] = {
    httpPut.PUT[String, HttpResponse](relationshipUrl(arn, nino).toString, "") map (_ => Unit)

  }

  def relationshipUrl(arn: Arn, nino: Nino) = {
    new URL(baseUrl, s"/agent-client-relationships/relationships/mtd-sa/${encodePathSegment(nino.value)}/${encodePathSegment(arn.value)}")
  }
}
