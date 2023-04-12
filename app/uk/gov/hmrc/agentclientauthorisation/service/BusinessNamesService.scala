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

package uk.gov.hmrc.agentclientauthorisation.service

import com.google.common.util.concurrent.RateLimiter
import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.DesConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.AgentServicesController.BusinessNameByUtr
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.http.HeaderCarrier

import java.util.concurrent.Executors
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

@Singleton
class BusinessNamesService @Inject()(desConnector: DesConnector)(implicit val appConfig: AppConfig) extends Logging {

  private val defaultBusinessName = ""

  private val fetchBusinessNamesRateLimiter = RateLimiter.create(appConfig.maxCallsPerSecondBusinessNames)

  private lazy val singleThreadedExecutionContext: ExecutionContext = ExecutionContext.fromExecutorService(Executors.newSingleThreadExecutor())

  /**
    * Uses a single-threaded execution context.
    * Concurrent requests to fetch business names get stream-lined.
    * Although the rate limiting requirement will be met, the "serial" nature of the concurrent requests can affect the time taken to execute individual requests.
    */
  def get(utrs: Set[String])(implicit headerCarrier: HeaderCarrier): Future[Set[BusinessNameByUtr]] = {
    implicit val ec: ExecutionContext = singleThreadedExecutionContext

    Future.sequence(utrs.map { utr =>
      (for {
        _            <- Future.sequence(Set(Future.successful(fetchBusinessNamesRateLimiter.acquire())))
        businessName <- get(Utr(utr))
      } yield {
        businessName
      }) transformWith {
        case Success(businessName) =>
          Future.successful(BusinessNameByUtr(utr, businessName))
        case Failure(ex) =>
          logger.error(s"Could not fetch business name for $utr: ${ex.getMessage}, returning default value '$defaultBusinessName'")
          Future.successful(BusinessNameByUtr(utr, defaultBusinessName))
      }
    })
  }

  def get(utr: Utr)(implicit headerCarrier: HeaderCarrier, ec: ExecutionContext): Future[String] =
    desConnector.getBusinessName(utr).map(_.getOrElse(defaultBusinessName))
}
