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

import org.scalatest.{BeforeAndAfterEach, Matchers, Suite, TestSuite}
import org.scalatestplus.play.OneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.FakeApplication
import reactivemongo.api.{DB, FailoverStrategy}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport, Awaiting => MongoAwaiting}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.it.Port

import scala.concurrent.ExecutionContext

trait AppAndStubs
    extends StartAndStopWireMock with StubUtils with OneServerPerSuite with DataStreamStubs with MetricsTestSupport
    with Matchers {
  me: Suite with TestSuite =>

  override lazy val port: Int = Port.randomAvailable

  implicit val hc = HeaderCarrier()
  implicit lazy val portNum: Int = port

  override lazy val app: Application = appBuilder.build()

  lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(additionalConfiguration)

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
      "microservice.services.agent-services-account.host"    -> wiremockHost,
      "microservice.services.agent-services-account.port"    -> wiremockPort,
      "microservice.services.des.host"                       -> wiremockHost,
      "microservice.services.des.port"                       -> wiremockPort,
      "microservice.services.ni-exemption-registration.host" -> wiremockHost,
      "microservice.services.ni-exemption-registration.port" -> wiremockPort,
      "microservice.services.email.host" -> wiremockHost,
      "microservice.services.email.port" -> wiremockPort,
      "auditing.enabled"                                     -> true,
      "auditing.consumer.baseUri.host"                       -> wiremockHost,
      "auditing.consumer.baseUri.port"                       -> wiremockPort,
      "Prod.auditing.consumer.baseUri.host"                  -> wiremockHost,
      "Prod.auditing.consumer.baseUri.port"                  -> wiremockPort,
      "clientStatus.cache.size"                              -> 1,
      "clientStatus.cache.expires"                           -> "1 millis",
      "invitation.expiryDuration"                            -> "14_days",
      "invitation-status-update-scheduler.enabled"           -> false,
      "invitation-status-update-scheduler.interval"          -> 5,
      "mongodb-migration.enabled"                            -> false
    )

  override def commonStubs(): Unit = {
    givenAuditConnector()
    givenCleanMetricRegistry()
  }

  private val generator = new Generator()
  def nextNino = generator.nextNino
}

trait MongoAppAndStubs extends AppAndStubs with MongoSpecSupport with ResetMongoBeforeTest with Matchers {
  me: Suite with TestSuite =>

  override implicit lazy val mongoConnectorForTest: MongoConnector =
    MongoConnector(mongoUri, Some(MongoApp.failoverStrategyForTest))

  lazy val db: InvitationsRepository = app.injector.instanceOf[InvitationsRepository]

  override protected def additionalConfiguration =
    super.additionalConfiguration + ("mongodb.uri" -> mongoUri)

}

trait ResetMongoBeforeTest extends BeforeAndAfterEach {
  me: Suite with MongoSpecSupport =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb()(implicit ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Unit =
    Awaiting.await(mongo().drop())
}

object Awaiting extends MongoAwaiting
