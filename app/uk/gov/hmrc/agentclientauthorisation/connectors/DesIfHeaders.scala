/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID
import javax.inject.{Inject, Singleton}

@Singleton
class DesIfHeaders @Inject()(appConfig: AppConfig) extends Logging {

  private val Environment = "Environment"
  private val CorrelationId = "CorrelationId"
  private val SessionId = "x-session-id"
  private val RequestId = "x-request-id"
  private val Authorization = "Authorization"

  private lazy val desEnvironment: String = appConfig.desEnvironment
  private lazy val desAuthorizationToken: String = appConfig.desAuthToken
  private lazy val ifEnvironment: String = appConfig.ifEnvironment
  private lazy val ifAuthTokenAPI1712: String = appConfig.ifAuthTokenAPI1712
  private lazy val ifAuthTokenAPI1495: String = appConfig.ifAuthTokenAPI1495
  private lazy val ifAuthTokenAPI2143: String = appConfig.ifAuthTokenAPI2143
  private lazy val ifAuthTokenAPI1171: String = appConfig.ifAuthTokenAPI1171

  def outboundHeaders(viaIF: Boolean, apiName: Option[String] = None)(implicit hc: HeaderCarrier): Seq[(String, String)] = {

    val baseHeaders = Seq(
      Environment   -> s"${if (viaIF) { ifEnvironment } else { desEnvironment }}",
      CorrelationId -> UUID.randomUUID().toString,
      SessionId     -> hc.sessionId.toString,
      RequestId     -> hc.requestId.toString
    )

    val api1171 = Seq(
      "GetRegistrationBusinessDetailsByMtdId",
      "GetRegistrationBusinessDetailsByNino",
      "GetTradingNameByNino"
    )

    if (viaIF) {
      apiName.fold(baseHeaders) {
        case "getTrustName"              => baseHeaders :+ Authorization -> s"Bearer $ifAuthTokenAPI1495"
        case "GetPptSubscriptionDisplay" => baseHeaders :+ Authorization -> s"Bearer $ifAuthTokenAPI1712"
        case "getPillar2Subscription"    => baseHeaders :+ Authorization -> s"Bearer $ifAuthTokenAPI2143"
        case s if api1171.contains(s)    => baseHeaders :+ Authorization -> s"Bearer $ifAuthTokenAPI1171"
        case _ =>
          logger.warn(s"Could not set $Authorization header for IF API '$apiName'")
          baseHeaders
      }
    } else {
      baseHeaders :+ Authorization -> s"Bearer $desAuthorizationToken"
    }

  }

}
