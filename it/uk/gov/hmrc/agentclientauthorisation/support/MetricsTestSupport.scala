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

package uk.gov.hmrc.agentclientauthorisation.support

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import org.scalatest.{Matchers, Suite}
import play.api.Application

import scala.collection.JavaConversions

trait MetricsTestSupport {
  self: Suite with Matchers =>

  def app: Application

  private var metricsRegistry: MetricRegistry = _

  def givenCleanMetricRegistry(): Unit = {
    val registry = app.injector.instanceOf[Metrics].defaultRegistry
    for (metric <- JavaConversions
                    .asScalaIterator[String](registry.getMetrics.keySet().iterator())) {
      registry.remove(metric)
    }
    metricsRegistry = registry
  }

  def verifyTimerExistsAndBeenUpdated(metric: String): Unit = {
    val timers = metricsRegistry.getTimers
    val metrics = timers.get(s"Timer-$metric")
    if (metrics == null)
      throw new Exception(s"Metric [$metric] not found, try one of ${timers.keySet()}")
    metrics.getCount should be >= 1L
  }

}
