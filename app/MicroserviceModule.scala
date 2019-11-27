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

import java.net.URLDecoder

import com.google.inject.name.Names
import com.google.inject.{AbstractModule, TypeLiteral}
import javax.inject.Provider
import org.slf4j.MDC
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentclientauthorisation.repository._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

class MicroserviceModule(val environment: Environment, val configuration: Configuration) extends AbstractModule {

  val runModeConfiguration: Configuration = configuration
  protected def mode = environment.mode

  def configure(): Unit = {
    val appName = "agent-client-authorisation"

    val loggerDateFormat: Option[String] = configuration.get[Option[String]]("logger.json.dateformat")
    Logger.info(s"Starting microservice : $appName : in mode : ${environment.mode}")
    MDC.put("appName", appName)
    loggerDateFormat.foreach(str => MDC.put("logger.json.dateformat", str))

    bindProperty("appName")
    //bindPropertyWithFun("old.auth.stride.enrolment", URLDecoder.decode(_, "utf-8"))
    //bindPropertyWithFun("new.auth.stride.enrolment")
    bind(classOf[AgentCacheProvider])
    bind(classOf[ScheduleRepository]).to(classOf[MongoScheduleRepository])
    bind(classOf[InvitationsRepository]).to(classOf[InvitationsRepositoryImpl])

    if (configuration.get[Option[Boolean]]("mongodb-migration.enabled").getOrElse(false)) {
      bind(classOf[RepositoryMigrationService]).asEagerSingleton()
    }

    if (configuration.get[Option[Boolean]]("invitation-status-update-scheduler.enabled").getOrElse(false)) {
      bind(classOf[InvitationsStatusUpdateScheduler]).asEagerSingleton()
    }
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

  import scala.reflect.ClassTag

  private def bindServiceConfigProperty[A](
    propertyName: String)(implicit classTag: ClassTag[A], ct: ServiceConfigPropertyType[A]): ScopedBindingBuilder =
    ct.bindServiceConfigProperty(classTag.runtimeClass.asInstanceOf[Class[A]])(propertyName)

  sealed trait ServiceConfigPropertyType[A] {
    def bindServiceConfigProperty(clazz: Class[A])(propertyName: String): ScopedBindingBuilder
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
