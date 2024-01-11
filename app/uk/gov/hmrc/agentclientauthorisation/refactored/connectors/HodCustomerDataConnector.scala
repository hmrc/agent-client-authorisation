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

package uk.gov.hmrc.agentclientauthorisation.refactored.connectors

import play.api.http.Status.OK
import uk.gov.hmrc.agentservice.{HodApiConfig, HodRequestConfig, HodResponse}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.net.URL
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class HodCustomerDataConnector @Inject()(httpV2: HttpClientV2) {
  def getRequest[T](apiConfig: HodApiConfig, requestConfig: HodRequestConfig[T])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[HodResponse[T]] = {

    val url = new URL(s"${apiConfig.baseUrl}${requestConfig.url}")

    httpV2
      .get(url)
      .setHeader(apiConfig.headers: _*)
      .execute[HttpResponse]
      .map { response =>
        response.status match {
          case OK    => response.json.as[HodResponse[T]](requestConfig.jsonReads)
          case other => HodResponse.apply(other)
        }
      }
  }

}
