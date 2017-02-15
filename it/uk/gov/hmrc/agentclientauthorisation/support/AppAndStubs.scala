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

package uk.gov.hmrc.agentclientauthorisation.support

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.OneServerPerSuite
import play.api.test.FakeApplication
import uk.gov.hmrc.mongo.{Awaiting => MongoAwaiting, MongoSpecSupport}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.it.Port

import scala.concurrent.ExecutionContext

trait AppAndStubs extends StartAndStopWireMock with StubUtils with OneServerPerSuite {
  me: Suite =>

  implicit val hc = HeaderCarrier()
  implicit val portNum = port

  override lazy val port: Int = Port.randomAvailable

  override implicit lazy val app: FakeApplication = FakeApplication(
    additionalConfiguration = additionalConfiguration
  )

  protected def additionalConfiguration: Map[String, Any] = {
    Map(
      "microservice.services.auth.host" -> wiremockHost,
      "microservice.services.auth.port" -> wiremockPort,
      "microservice.services.agencies-fake.host" -> wiremockHost,
      "microservice.services.agencies-fake.port" -> wiremockPort,
      "microservice.services.relationships.host" -> wiremockHost,
      "microservice.services.relationships.port" -> wiremockPort,
      "microservice.services.etmp.host" -> wiremockHost,
      "microservice.services.etmp.port" -> wiremockPort
    )
  }
}

trait MongoAppAndStubs extends AppAndStubs with MongoSpecSupport with ResetMongoBeforeTest {
  me: Suite =>

  override protected def additionalConfiguration =
    super.additionalConfiguration + ("mongodb.uri" -> mongoUri)
}

trait ResetMongoBeforeTest extends BeforeAndAfterEach {
  me: Suite with MongoSpecSupport =>

  override def beforeEach(): Unit = {
    super.beforeEach()
    dropMongoDb()
  }

  def dropMongoDb()(implicit ec: ExecutionContext = scala.concurrent.ExecutionContext.global): Unit = {
    Awaiting.await(mongo().drop())
  }
}

object Awaiting extends MongoAwaiting
