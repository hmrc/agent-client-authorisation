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
import play.api.http.HttpFilters
import play.api.mvc._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.DB
import uk.gov.hmrc.api.config._
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, ServicesConfig}
import uk.gov.hmrc.play.graphite.GraphiteConfig
import uk.gov.hmrc.play.microservice.bootstrap.JsonErrorHandling
import uk.gov.hmrc.play.microservice.bootstrap.Routing.RemovingOfTrailingSlashes
import uk.gov.hmrc.play.microservice.config.ErrorAuditingSettings
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.whitelist.AkamaiWhitelistFilter

class GuiceModule() extends AbstractModule with ServicesConfig {
  def configure() = {
    bind(classOf[HttpGet]).toInstance(WSHttp)
    bind(classOf[HttpPut]).toInstance(WSHttp)
    bind(classOf[AuditConnector]).to(classOf[MicroserviceAuditConnector])
    bind(classOf[DB]).toProvider(classOf[MongoDbProvider])
    bind(classOf[ControllerConfig]).to(classOf[ControllerConfiguration])
    bind(classOf[AuditFilter]).to(classOf[MicroserviceAuditFilter])
    bind(classOf[ServicesConfig]).toInstance(MicroserviceGlobal)
    bindBaseUrl("auth")
    bindBaseUrl("agencies-fake")
    bindBaseUrl("relationships")
    bindBaseUrl("afi-relationships")
    bindBaseUrl("des")
    bindProperty("des.environment", "des.environment")
    bindProperty("des.authorizationToken", "des.authorization-token")
    bindProperty("invitation.expiryDuration", "invitation.expiryDuration")
  }

  private def bindBaseUrl(serviceName: String) =
    bind(classOf[URL]).annotatedWith(Names.named(s"$serviceName-baseUrl")).toProvider(new BaseUrlProvider(serviceName))

  private class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(baseUrl(serviceName))
  }

  private def bindProperty(objectName: String, propertyName: String) =
    bind(classOf[String]).annotatedWith(Names.named(objectName)).toProvider(new PropertyProvider(propertyName))

  private class PropertyProvider(confKey: String) extends Provider[String] {
    override lazy val get = getConfString(confKey, throw new IllegalStateException(s"No value found for configuration property $confKey"))
  }

}

class MongoDbProvider @Inject()(reactiveMongoComponent: ReactiveMongoComponent) extends Provider[DB] {
  def get = reactiveMongoComponent.mongoConnector.db()
}

@Singleton
class ControllerConfiguration @Inject()(configuration: Configuration) extends ControllerConfig {
  lazy val controllerConfigs = configuration.underlying.as[Config]("controllers")
}

@Singleton
class AuthParamsControllerConfiguration @Inject()(controllerConfig: ControllerConfig) {
  lazy val controllerConfigs = controllerConfig.controllerConfigs
}

@Singleton
class MicroserviceAuditFilter @Inject()(override val auditConnector: AuditConnector, controllerConfig: ControllerConfig) extends AuditFilter with AppName with MicroserviceFilterSupport {
  override def controllerNeedsAuditing(controllerName: String) = controllerConfig.paramsForController(controllerName).needsAuditing
}

@Singleton
class MicroserviceLoggingFilter @Inject()(controllerConfig: ControllerConfig) extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = controllerConfig.paramsForController(controllerName).needsLogging
}

@Singleton
class WhitelistFilter @Inject()(configuration: Configuration) extends AkamaiWhitelistFilter with MicroserviceFilterSupport {

  def enabled(): Boolean = configuration.getBoolean("microservice.whitelist.enabled").getOrElse(true)

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

class Filters @Inject()(
                         whitelistFilter: WhitelistFilter,
                         metricsFilter: MetricsFilter,
                         auditFilter: MicroserviceAuditFilter,
                         loggingFilter: MicroserviceLoggingFilter
                       ) extends HttpFilters {

  private lazy val whitelistFilterSeq = if (whitelistFilter.enabled()) {
    Logger.info("Starting microservice with IP whitelist enabled")
    Seq(whitelistFilter)
  } else {
    Logger.info("Starting microservice with IP whitelist disabled")
    Seq.empty
  }

  override def filters = whitelistFilterSeq ++ Seq(
    metricsFilter,
    auditFilter,
    loggingFilter)
}

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
  override lazy val slConnector = ServiceLocatorConnector(WSHttp)
  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")
}
