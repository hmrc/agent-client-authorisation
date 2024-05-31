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


import com.google.inject.AbstractModule
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, Suite, TestSuite}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport

trait AppAndStubs
    extends StartAndStopWireMock with GuiceOneServerPerSuite with DataStreamStubs with MetricsTestSupport
    with Matchers {
  me: Suite with TestSuite =>

  implicit val hc = HeaderCarrier(authorization = Some(Authorization("Bearer testtoken")))
  implicit lazy val portNum: Int = port

  override lazy val app: Application = appBuilder.build()

  lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)
    .overrides(moduleWithOverrides)

  protected def moduleWithOverrides = new AbstractModule {}

  protected def additionalConfiguration: Map[String, Any] =
    Map(
      "microservice.services.auth.host"                      -> wiremockHost,
      "microservice.services.auth.port"                      -> wiremockPort,
      "microservice.services.agencies-fake.host"             -> wiremockHost,
      "microservice.services.agencies-fake.port"             -> wiremockPort,
      "microservice.services.relationships.host"             -> wiremockHost,
      "microservice.services.relationships.port"             -> wiremockPort,
      "microservice.services.afi-relationships.host"         -> wiremockHost,
      "microservice.services.afi-relationships.port"         -> wiremockPort,
      "microservice.services.citizen-details.host"           -> wiremockHost,
      "microservice.services.citizen-details.port"           -> wiremockPort,
      "microservice.services.enrolment-store-proxy.host"     -> wiremockHost,
      "microservice.services.enrolment-store-proxy.port"     -> wiremockPort,
      "microservice.services.des.host"                       -> wiremockHost,
      "microservice.services.des.port"                       -> wiremockPort,
      "microservice.services.if.host"                        -> wiremockHost,
      "microservice.services.if.port"                        -> wiremockPort,
      "microservice.services.ni-exemption-registration.host" -> wiremockHost,
      "microservice.services.ni-exemption-registration.port" -> wiremockPort,
      "microservice.services.email.host"                     -> wiremockHost,
      "microservice.services.email.port"                     -> wiremockPort,
      "microservice.services.platform-analytics.host"        -> wiremockHost,
      "microservice.services.platform-analytics.port"        -> wiremockPort,
      "microservice.services.platform-analytics.throttling-calls-per-second"        -> 0,
      "auditing.enabled"                                     -> true,
      "auditing.consumer.baseUri.host"                       -> wiremockHost,
      "auditing.consumer.baseUri.port"                       -> wiremockPort,
      "Prod.auditing.consumer.baseUri.host"                  -> wiremockHost,
      "Prod.auditing.consumer.baseUri.port"                  -> wiremockPort,
      "agent.cache.size"                                     -> 1,
      "agent.cache.expires"                                  -> "1 millis",
      "invitation.expiryDuration"                            -> "21 days",
      "invitation-status-update-scheduler.enabled"           -> false,
      "invitation-status-update-scheduler.interval"          -> 20,
      "google-analytics.batchSize"                           -> 2,
      "google-analytics.token"                               -> "token",
      "des-if.enabled"                                        -> false,
      "alt-itsa.enabled"                                       -> true,
      "remove-personal-info-scheduler.enabled"              -> false
    )

  override def commonStubs(): Unit = {
    givenAuditConnector()
    givenCleanMetricRegistry()
  }

  private val generator = new Generator()
  def nextNino = generator.nextNino
}

trait MongoAppAndStubs extends AppAndStubs with MongoSupport with ResetMongoBeforeTest with Matchers {
  me: Suite with TestSuite =>

  override def moduleWithOverrides: AbstractModule = new AbstractModule {
    override def configure: Unit = {
     // bind(classOf[MongoComponent]).toInstance(mongoComponent)
    }
  }

  override protected def additionalConfiguration =
    super.additionalConfiguration + ("mongodb.uri" -> mongoUri)

}

trait ResetMongoBeforeTest extends BeforeAndAfterEach {
  me: Suite with MongoSupport =>

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    dropDatabase()
  }
}
