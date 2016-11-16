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

import java.net.URL
import java.util.Base64
import javax.inject._

import com.google.inject.AbstractModule
import com.google.inject.name.Names
import com.kenshoo.play.metrics.MetricsFilter
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import org.slf4j.MDC
import play.api._
import play.api.http.DefaultHttpFilters
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.api.config._
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.play.audit.filters.AuditFilter
import uk.gov.hmrc.play.audit.http.config.ErrorAuditingSettings
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.controllers.AuthParamsControllerConfig
import uk.gov.hmrc.play.auth.microservice.connectors.AuthConnector
import uk.gov.hmrc.play.auth.microservice.filters.AuthorisationFilter
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, ServicesConfig}
import uk.gov.hmrc.play.filters._
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.http.logging.filters.LoggingFilter
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet, HttpPut}
import uk.gov.hmrc.play.microservice.bootstrap.JsonErrorHandling
import uk.gov.hmrc.play.microservice.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter

class GuiceModule() extends AbstractModule with ServicesConfig {
  def configure() = {
    bind(classOf[HttpGet]).to(classOf[WSHttp])
    bind(classOf[HttpPut]).to(classOf[WSHttp])
    bind(classOf[AuditConnector]).to(classOf[MicroserviceAuditConnector])
    bind(classOf[DB]).toProvider(classOf[MongoDbProvider])
    bind(classOf[ControllerConfig]).to(classOf[ControllerConfiguration])
    bind(classOf[AuthParamsControllerConfig]).to(classOf[AuthParamsControllerConfiguration])
    bind(classOf[AuditFilter]).to(classOf[MicroserviceAuditFilter])
    bind(classOf[AuthConnector]).to(classOf[MicroserviceAuthConnector])
    bind(classOf[ServicesConfig]).toInstance(MicroserviceGlobal)
    bindBaseUrl("auth")
    bindBaseUrl("agencies-fake")
    bindBaseUrl("relationships")
  }

  private def bindBaseUrl(serviceName: String) =
    bind(classOf[URL]).annotatedWith(Names.named(s"$serviceName-baseUrl")).toProvider(new BaseUrlProvider(serviceName))

  private class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }

}

class MongoDbProvider @Inject() (reactiveMongoComponent: ReactiveMongoComponent) extends Provider[DB] {
  def get = reactiveMongoComponent.mongoConnector.db()
}

// TODO These could probably be replaced with instance bindings.
@Singleton
class ControllerConfiguration @Inject() (configuration: Configuration) extends ControllerConfig {
  lazy val controllerConfigs = configuration.underlying.as[Config]("controllers")
}

@Singleton
class AuthParamsControllerConfiguration @Inject() (controllerConfig: ControllerConfig) extends AuthParamsControllerConfig {
  lazy val controllerConfigs = controllerConfig.controllerConfigs
}

@Singleton
class MicroserviceAuditFilter @Inject() (override val auditConnector: AuditConnector, controllerConfig: ControllerConfig) extends AuditFilter with AppName with MicroserviceFilterSupport {
  override def controllerNeedsAuditing(controllerName: String) = controllerConfig.paramsForController(controllerName).needsAuditing
}

@Singleton
class MicroserviceLoggingFilter @Inject()(controllerConfig: ControllerConfig) extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = controllerConfig.paramsForController(controllerName).needsLogging
}

@Singleton
class MicroserviceAuthFilter @Inject() (
      override val authParamsConfig: AuthParamsControllerConfig,
      override val authConnector: AuthConnector,
      controllerConfig: ControllerConfig) extends AuthorisationFilter with MicroserviceFilterSupport {
  override def controllerNeedsAuth(controllerName: String): Boolean = controllerConfig.paramsForController(controllerName).needsAuth
}

@Singleton
class WhitelistFilter @Inject() (configuration: Configuration) extends AkamaiWhitelistFilter with MicroserviceFilterSupport {

  override val whitelist: Seq[String] = whitelistConfig("microservice.whitelist.ips")
  override val destination: Call = Call("GET", "/agent-client-authorisation/forbidden")
  override val excludedPaths: Seq[Call] = Seq(
    Call("GET", "/ping/ping"),
    Call("GET", "/admin/details"),
    Call("GET", "/admin/metrics"),
    Call("GET", "/agent-access-control/unauthorised")
  )

  private def whitelistConfig(key: String): Seq[String] =
    new String(Base64.getDecoder.decode(configuration.getString(key).getOrElse("")), "UTF-8").split(",")
}
// TODO re-add whitelist filter once there is a 2.5 compatible version
class Filters @Inject() (
  metricsFilter: MetricsFilter,
  auditFilter: MicroserviceAuditFilter,
  loggingFilter: MicroserviceLoggingFilter,
  authFilter: MicroserviceAuthFilter
) extends DefaultHttpFilters (
  metricsFilter,
  auditFilter,
  loggingFilter,
  authFilter)

object MicroserviceGlobal
  extends GlobalSettings
  with GraphiteConfig
  with RemovingOfTrailingSlashes
  with JsonErrorHandling
  with ErrorAuditingSettings
  with ServiceLocatorRegistration
  with ServiceLocatorConfig {

  lazy val appName = Play.current.configuration.getString("appName").getOrElse("APP NAME NOT SET")
  lazy val loggerDateFormat: Option[String] = Play.current.configuration.getString("logger.json.dateformat")

  override def onStart(app: Application) {
    Logger.info(s"Starting microservice : $appName : in mode : ${app.mode}")
    MDC.put("appName", appName)
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))
    super.onStart(app)
  }

  override val auditConnector = new MicroserviceAuditConnector
  override lazy val slConnector = ServiceLocatorConnector(new WSHttp(auditConnector))
  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
}

