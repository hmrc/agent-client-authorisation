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

package uk.gov.hmrc.agentclientauthorisation.service

import com.codahale.metrics.MetricRegistry
import com.github.blemale.scaffeine.Scaffeine
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Provider, Singleton}
import play.api.{Configuration, Environment, Logger}
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientStatusController.ClientStatus
import uk.gov.hmrc.agentclientauthorisation.model.TrustResponse
import uk.gov.hmrc.play.config.ServicesConfig

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Success

trait ClientStatusCache extends Cache[ClientStatus]
trait TrustResponseCache extends Cache[TrustResponse]

trait Cache[T] {
  def apply(key: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[T]
}

class DoNotCache[T] {
  def apply(key: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[T] = body
}

abstract class LocalCaffeineCache[T](name: String, size: Int, expires: Duration) extends KenshooCacheMetrics {

  val underlying: com.github.blemale.scaffeine.Cache[String, T] =
    Scaffeine()
      .expireAfterWrite(expires)
      .maximumSize(size)
      .build[String, T]()

  def apply(key: String)(body: => Future[T])(implicit ec: ExecutionContext): Future[T] =
    underlying.getIfPresent(key) match {
      case Some(v) =>
        record("Count-" + name + "-from-cache")
        Future.successful(v)
      case None =>
        body.andThen {
          case Success(v) =>
            Logger(getClass).info(s"Missing $name cache hit, storing new value.")
            record("Count-" + name + "-from-source")
            underlying.put(key, v)
        }
    }
}

trait KenshooCacheMetrics {

  val kenshooRegistry: MetricRegistry

  def record[T](name: String): Unit = {
    kenshooRegistry.getMeters.getOrDefault(name, kenshooRegistry.meter(name)).mark()
    Logger(getClass).debug(s"kenshoo-event::meter::$name::recorded")
  }

}

@Singleton
class ClientStatusCacheProvider @Inject()(val environment: Environment, configuration: Configuration, metrics: Metrics)
    extends Provider[ClientStatusCache] with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override def mode = environment.mode

  override val get: ClientStatusCache = new LocalCaffeineCache[ClientStatus](
    "ClientStatus",
    configuration.underlying.getInt("clientStatus.cache.size"),
    Duration.create(configuration.underlying.getString("clientStatus.cache.expires"))
  ) with ClientStatusCache with KenshooCacheMetrics {
    override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry
  }
}

@Singleton
class TrustResponseCacheProvider @Inject()(val environment: Environment, configuration: Configuration, metrics: Metrics)
    extends Provider[TrustResponseCache] with ServicesConfig {

  override val runModeConfiguration: Configuration = configuration
  override def mode = environment.mode

  override val get: TrustResponseCache = new LocalCaffeineCache[TrustResponse](
    "TrustResponse",
    configuration.underlying.getInt("trustResponse.cache.size"),
    Duration.create(configuration.underlying.getString("trustResponse.cache.expires"))
  ) with TrustResponseCache with KenshooCacheMetrics {
    override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry
  }
}
