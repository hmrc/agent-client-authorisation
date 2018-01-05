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

package uk.gov.hmrc.agentclientauthorisation

import java.util.regex.{Matcher, Pattern}
import javax.inject.{Inject, Singleton}

import app.Routes
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.mvc.{Filter, RequestHeader, Result}
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter.fromHeadersAndSession
import uk.gov.hmrc.play.microservice.filters.MicroserviceFilterSupport

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class MicroserviceMonitoringFilter @Inject()(metrics: Metrics, routes: Routes)
                                            (implicit ec: ExecutionContext)
  extends MonitoringFilter(RoutesConverter.keyToPatternMapping(routes, Set()), metrics.defaultRegistry) with MicroserviceFilterSupport

object RoutesConverter {
  def keyToPatternMapping(routes: Routes, variables: Set[String] = Set.empty): Seq[(String, String)] = {
    routes.documentation.map {
      case (method, route, _) => {
        val r = route.replace("<[^/]+>", "")
        val key = stripInitialAndTrailingSlash(r).split("/").map(
          p => if (p.startsWith("$")) {
            val name = p.substring(1)
            if (variables.contains(name)) s"{$name}" else ""
          } else p).mkString("-")
        val pattern = r.replace("$", ":")
        Logger.info(s"$key-$method -> $pattern")
        (key, pattern)
      }
    }
  }

  private def stripInitialAndTrailingSlash(key: String): String = {
    val k = if (key.startsWith("/")) key.substring(1) else key
    if (k.endsWith("/")) k.substring(0, k.length - 1) else k
  }
}

abstract class MonitoringFilter(val keyToPatternMapping: Seq[(String, String)], override val kenshooRegistry: MetricRegistry)
                               (implicit ec: ExecutionContext)
  extends Filter with HttpAPIMonitor with MonitoringKeyMatcher {

  override def apply(nextFilter: (RequestHeader) => Future[Result])(requestHeader: RequestHeader): Future[Result] = {

    implicit val hc: HeaderCarrier = fromHeadersAndSession(requestHeader.headers)

    findMatchingKey(requestHeader.uri) match {
      case Some(key) =>
        monitor(s"API-$key-${requestHeader.method}") {
          nextFilter(requestHeader)
        }
      case None =>
        Logger.debug(s"API-Not-Monitored: ${requestHeader.method}-${requestHeader.uri}")
        nextFilter(requestHeader)
    }
  }
}

trait MonitoringKeyMatcher {

  private val placeholderPattern = Pattern.compile("(:[^/]+)")

  def keyToPatternMapping: Seq[(String, String)]

  private lazy val patterns: Seq[(String, (Pattern, Seq[String]))] = keyToPatternMapping
    .map { case (k, p) => (k, preparePatternAndVariables(p)) }
    .map { case (k, (p, vs)) => (k, (Pattern.compile(p), vs)) }

  def preparePatternAndVariables(p: String): (String, Seq[String]) = {
    var pattern = p
    val m = placeholderPattern.matcher(pattern)
    var variables = Seq[String]()
    while (m.find()) {
      val variable = m.group().substring(1)
      if (variables.contains(variable)) {
        throw new IllegalArgumentException(s"Duplicated variable name '$variable' in monitoring filter pattern '$p'")
      }
      variables = variables :+ variable
    }
    for (v <- variables) {
      pattern = pattern.replace(":" + v, "([^/]+)")
    }
    ("^.*" + pattern + "$", variables.map("{" + _ + "}"))
  }

  def findMatchingKey(value: String): Option[String] = {
    patterns.collectFirst {
      case (key, (pattern, variables)) if pattern.matcher(value).matches() =>
        (key, variables, readValues(pattern.matcher(value)))
    } map {
      case (key, variables, values) => replaceVariables(key, variables, values)
    }
  }

  private def readValues(result: Matcher): Seq[String] = {
    result.matches()
    (1 to result.groupCount()) map result.group
  }

  private def replaceVariables(key: String, variables: Seq[String], values: Seq[String]): String = {
    if (values.isEmpty) key
    else values.zip(variables).foldLeft(key) {
      case (k, (value, variable)) => k.replace(variable, value.toLowerCase)
    }
  }

}
