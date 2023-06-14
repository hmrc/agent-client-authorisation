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

package uk.gov.hmrc.agentclientauthorisation.support
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import com.typesafe.config.Config
import org.scalamock.scalatest.MockFactory
import play.api.{Configuration, Environment}
import uk.gov.hmrc.agentclientauthorisation.connectors.{CitizenDetailsConnector, DesConnector, EisConnector}
import uk.gov.hmrc.agentclientauthorisation.service.AgentCacheProvider
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

trait MocksWithCache extends MockFactory {
  val mockConfig: Config = mock[Config]
  val mockConfiguration = new Configuration(mockConfig)
  val mockEnv: Environment = mock[Environment]
  implicit val mockMetrics: Metrics = mock[Metrics]
  val mockServicesConfig: ServicesConfig = mock[ServicesConfig]

  val mockCitizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]
  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockEisConnector: EisConnector = mock[EisConnector]

  (mockMetrics.defaultRegistry _).expects().returns(new MetricRegistry()).anyNumberOfTimes()

  (mockConfig.getInt(_: String)).expects("agent.cache.size").returns(1).anyNumberOfTimes()
  (mockConfig.getString(_: String)).expects("agent.cache.expires").returns("1 hour")
  (mockConfig.getBoolean(_: String)).expects("agent.cache.enabled").returns(true)

  val agentCacheProvider: AgentCacheProvider = new AgentCacheProvider(mockEnv, mockConfiguration, mockServicesConfig)

}
