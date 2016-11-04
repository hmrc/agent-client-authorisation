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

package uk.gov.hmrc.agentclientauthorisation

import java.util.Base64

import com.typesafe.config.Config
import play.api.{Application, Configuration, Logger, Play}
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode}
import uk.gov.hmrc.play.filters.MicroserviceFilterSupport
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import net.ceedubs.ficus.Ficus._
import play.api.mvc.Call
import uk.gov.hmrc.api.config.{ServiceLocatorConfig, ServiceLocatorRegistration}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter


object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object AuthParamsControllerConfiguration extends AuthParamsControllerConfig {
  lazy val controllerConfigs = ControllerConfiguration.controllerConfigs
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceAuthFilter extends AuthorisationFilter with MicroserviceFilterSupport {
  override lazy val authParamsConfig = AuthParamsControllerConfiguration
  override lazy val authConnector = MicroserviceAuthConnector
  override def controllerNeedsAuth(controllerName: String): Boolean = ControllerConfiguration.paramsForController(controllerName).needsAuth
}
class WhitelistFilter extends AkamaiWhitelistFilter with MicroserviceFilterSupport {
  import play.api.Play.current

  override val whitelist: Seq[String] = whitelistConfig("microservice.whitelist.ips")
  override val destination: Call = Call("GET", "/agent-client-authorisation/forbidden")
  override val excludedPaths: Seq[Call] = Seq(
    Call("GET", "/ping/ping"),
    Call("GET", "/admin/details"),
    Call("GET", "/admin/metrics"),
    Call("GET", "/agent-access-control/unauthorised")
  )

  private def whitelistConfig(key: String): Seq[String] =
    new String(Base64.getDecoder.decode(Play.configuration.getString(key).getOrElse("")), "UTF-8").split(",")
}

object WhitelistFilter {
  import play.api.Play.current

  def enabled(): Boolean = Play.configuration.getBoolean("microservice.whitelist.enabled").getOrElse(true)

}

trait MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with ServiceRegistry with ControllerRegistry with ServiceLocatorRegistration with ServiceLocatorConfig {
  private lazy val whitelistFilterSeq = WhitelistFilter.enabled() match {
    case true =>
      Logger.info("Starting microservice with IP whitelist enabled")
      Seq(new WhitelistFilter)
    case _ =>
      Logger.info("Starting microservice with IP whitelist disabled")
      Seq.empty
  }

  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = Some(MicroserviceAuthFilter)

  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override lazy val microserviceFilters = whitelistFilterSeq ++ defaultMicroserviceFilters

//  override def getControllerInstance[A](controllerClass: Class[A]): A = {
//    getController(controllerClass)
//  }
}

object MicroserviceGlobal extends MicroserviceGlobal
