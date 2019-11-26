/*
 * Copyright 2019 HM Revenue & Customs
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

import java.net.{URL, URLDecoder}

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import javax.inject.Provider
import org.slf4j.MDC
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentclientauthorisation.connectors.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.repository._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient


class MicroserviceModule(val environment: Environment, val configuration: Configuration, servicesConfig: ServicesConfig)
    extends AbstractModule {

  val runModeConfiguration: Configuration = configuration
  protected def mode = environment.mode

  def configure(): Unit = {
    val appName = "agent-client-authorisation"

    val loggerDateFormat: Option[String] = configuration.get[Option[String]]("logger.json.dateformat")
    Logger.info(s"Starting microservice : $appName : in mode : ${environment.mode}")
    MDC.put("appName", appName)
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))

    bindProperty("appName")
    bindPropertyWithFun("old.auth.stride.enrolment", URLDecoder.decode(_, "utf-8"))
    bindPropertyWithFun("new.auth.stride.enrolment")
    bind(classOf[HttpGet]).to(classOf[DefaultHttpClient])
    bind(classOf[HttpPut]).to(classOf[DefaultHttpClient])
    bind(classOf[HttpPost]).to(classOf[DefaultHttpClient])
    bind(classOf[AuthConnector]).to(classOf[MicroserviceAuthConnector])
    bind(classOf[AgentCacheProvider])
    bind(classOf[ScheduleRepository]).to(classOf[MongoScheduleRepository])
    bind(classOf[InvitationsRepository]).to(classOf[InvitationsRepositoryImpl])

    bindBaseUrl("auth")
    bindBaseUrl("agencies-fake")
    bindBaseUrl("relationships")
    bindBaseUrl("afi-relationships")
    bindBaseUrl("agent-services-account")
    bindBaseUrl("des")
    bindBaseUrl("citizen-details")
    bindBaseUrl("ni-exemption-registration")
    bindBaseUrl("email")
    bindProperty2param("des.environment", "des.environment")
    bindProperty2param("des.authorizationToken", "des.authorization-token")
    bindPropertyWithFun("invitation.expiryDuration", _.replace("_", " "))
    bindProperty2param("agent-invitations-frontend.external-url", "agent-invitations-frontend.external-url")
    bindIntegerProperty("invitation-status-update-scheduler.interval")
    bindBooleanProperty("invitation-status-update-scheduler.enabled")
    bindBooleanProperty("mongodb-migration.enabled")

    if (configuration.get[Option[Boolean]]("mongodb-migration.enabled").getOrElse(false)) {
      bind(classOf[RepositoryMigrationService]).asEagerSingleton()
    }

    if (configuration.get[Option[Boolean]]("invitation-status-update-scheduler.enabled").getOrElse(false)) {
      bind(classOf[InvitationsStatusUpdateScheduler]).asEagerSingleton()
    }
  }

  private def bindBaseUrl(serviceName: String) =
    bind(classOf[URL]).annotatedWith(Names.named(s"$serviceName-baseUrl")).toProvider(new BaseUrlProvider(serviceName))

  private class BaseUrlProvider(serviceName: String) extends Provider[URL] {
    override lazy val get = new URL(servicesConfig.baseUrl(serviceName))
  }

  private def bindProperty2param(objectName: String, propertyName: String) =
    bind(classOf[String]).annotatedWith(Names.named(objectName)).toProvider(new PropertyProvider2param(propertyName))

  private class PropertyProvider2param(confKey: String) extends Provider[String] {
    override lazy val get =
      servicesConfig.getString(confKey)
  }

  private def bindProperty(propertyName: String) =
    bind(classOf[String]).annotatedWith(Names.named(propertyName)).toProvider(new PropertyProvider(propertyName))

  private class PropertyProvider(confKey: String) extends Provider[String] {
    override lazy val get = configuration
      .get[Option[String]](confKey)
      .getOrElse(throw new IllegalStateException(s"No value found for configuration property $confKey"))
  }

  private def bindPropertyWithFun(propertyName: String, mapFx: String => String = identity) =
    bind(classOf[String])
      .annotatedWith(Names.named(propertyName))
      .toProvider(new PropertyProviderWithFun(propertyName, mapFx))

  private class PropertyProviderWithFun(confKey: String, mapFx: String => String) extends Provider[String] {
    override lazy val get = configuration
      .get[Option[String]](confKey)
      .map(mapFx)
      .getOrElse(throw new IllegalStateException(s"No value found for configuration property $confKey"))
  }

  private def bindSeqStringProperty(propertyName: String) =
    bind(new TypeLiteral[Seq[String]]() {})
      .annotatedWith(Names.named(propertyName))
      .toProvider(new SeqStringPropertyProvider(propertyName))

  private class SeqStringPropertyProvider(confKey: String) extends Provider[Seq[String]] {
    override lazy val get: Seq[String] = configuration
      .get[Option[Seq[String]]](confKey)
      .getOrElse(throw new IllegalStateException(s"No value found for configuration property $confKey"))
      .map(URLDecoder.decode(_, "utf-8"))

  }

  import com.google.inject.binder.ScopedBindingBuilder
  import com.google.inject.name.Names.named

  import scala.reflect.ClassTag

  private def bindServiceConfigProperty[A](
    propertyName: String)(implicit classTag: ClassTag[A], ct: ServiceConfigPropertyType[A]): ScopedBindingBuilder =
    ct.bindServiceConfigProperty(classTag.runtimeClass.asInstanceOf[Class[A]])(propertyName)

  sealed trait ServiceConfigPropertyType[A] {
    def bindServiceConfigProperty(clazz: Class[A])(propertyName: String): ScopedBindingBuilder
  }

  object ServiceConfigPropertyType {

    implicit val stringServiceConfigProperty: ServiceConfigPropertyType[String] =
      new ServiceConfigPropertyType[String] {
        def bindServiceConfigProperty(clazz: Class[String])(propertyName: String): ScopedBindingBuilder =
          bind(clazz)
            .annotatedWith(named(s"$propertyName"))
            .toProvider(new StringServiceConfigPropertyProvider(propertyName))

        private class StringServiceConfigPropertyProvider(propertyName: String) extends Provider[String] {
          override lazy val get = servicesConfig.getString(
            propertyName)
        }
      }

    implicit val intServiceConfigProperty: ServiceConfigPropertyType[Int] = new ServiceConfigPropertyType[Int] {
      def bindServiceConfigProperty(clazz: Class[Int])(propertyName: String): ScopedBindingBuilder =
        bind(clazz)
          .annotatedWith(named(s"$propertyName"))
          .toProvider(new IntServiceConfigPropertyProvider(propertyName))

      private class IntServiceConfigPropertyProvider(propertyName: String) extends Provider[Int] {
        override lazy val get = servicesConfig.getInt(
          propertyName)
      }
    }

    implicit val booleanServiceConfigProperty: ServiceConfigPropertyType[Boolean] =
      new ServiceConfigPropertyType[Boolean] {
        def bindServiceConfigProperty(clazz: Class[Boolean])(propertyName: String): ScopedBindingBuilder =
          bind(clazz)
            .annotatedWith(named(s"$propertyName"))
            .toProvider(new BooleanServiceConfigPropertyProvider(propertyName))

        private class BooleanServiceConfigPropertyProvider(propertyName: String) extends Provider[Boolean] {
          override lazy val get = servicesConfig.getBoolean(propertyName)
        }
      }
  }

  private def bindBooleanProperty(propertyName: String) =
    bind(classOf[Boolean])
      .annotatedWith(Names.named(propertyName))
      .toProvider(new BooleanPropertyProvider(propertyName))

  private class BooleanPropertyProvider(confKey: String) extends Provider[Boolean] {
    def getBooleanFromRoot =
      runModeConfiguration
        .get[Option[Boolean]](confKey)
        .getOrElse(throw new IllegalStateException(s"No value found for configuration property $confKey"))
    override lazy val get: Boolean = servicesConfig.getBoolean(confKey)
  }

  private def bindIntegerProperty(propertyName: String) =
    bind(classOf[Int])
      .annotatedWith(Names.named(propertyName))
      .toProvider(new IntegerPropertyProvider(propertyName))

  private class IntegerPropertyProvider(confKey: String) extends Provider[Int] {
    override lazy val get: Int = configuration
      .get[Option[Int]](confKey)
      .getOrElse(throw new IllegalStateException(s"No value found for configuration property $confKey"))
  }

}
