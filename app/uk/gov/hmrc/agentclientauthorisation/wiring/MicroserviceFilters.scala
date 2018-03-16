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

package uk.gov.hmrc.agentclientauthorisation.wiring

import java.util.Base64

import javax.inject.{ Inject, Singleton }
import com.kenshoo.play.metrics.MetricsFilter
import play.api.{ Configuration, Logger }
import play.api.http.DefaultHttpFilters
import play.api.mvc.{ Call, EssentialFilter }
import uk.gov.hmrc.play.bootstrap.filters.{ AuditFilter, CacheControlFilter, LoggingFilter }
import uk.gov.hmrc.play.microservice.filters.MicroserviceFilterSupport
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter

@Singleton
class MicroserviceFilters @Inject() (
  metricsFilter: MetricsFilter,
  auditFilter: AuditFilter,
  loggingFilter: LoggingFilter,
  cacheFilter: CacheControlFilter,
  monitoringFilter: MicroserviceMonitoringFilter,
  whitelistFilter: WhitelistFilter) extends DefaultHttpFilters(Seq(metricsFilter, monitoringFilter, auditFilter, loggingFilter, cacheFilter) ++ MicroserviceFilters.whitelistFilterSeq(whitelistFilter): _*)

@Singleton
class WhitelistFilter @Inject() (configuration: Configuration) extends AkamaiWhitelistFilter with MicroserviceFilterSupport {

  override val whitelist: Seq[String] = whitelistConfig("microservice.whitelist.ips")
  override val destination: Call = Call("GET", "/agent-client-authorisation/forbidden")
  override val excludedPaths: Seq[Call] = Seq(
    Call("GET", "/ping/ping"),
    Call("GET", "/admin/details"),
    Call("GET", "/admin/metrics"),
    Call("GET", "/agent-client-authorisation/forbidden"))

  def enabled(): Boolean = {
    configuration.getBoolean("microservice.whitelist.enabled").getOrElse(true)
  }

  private def whitelistConfig(key: String): Seq[String] =
    new String(Base64.getDecoder().decode(configuration.getString(key).getOrElse("")), "UTF-8").split(",")
}

object MicroserviceFilters {

  def whitelistFilterSeq(whitelistFilter: WhitelistFilter) = if (whitelistFilter.enabled()) {
    Logger.info("Starting microservice with IP whitelist enabled")
    Seq(whitelistFilter.asInstanceOf[EssentialFilter])
  } else {
    Logger.info("Starting microservice with IP whitelist disabled")
    Seq.empty
  }
}