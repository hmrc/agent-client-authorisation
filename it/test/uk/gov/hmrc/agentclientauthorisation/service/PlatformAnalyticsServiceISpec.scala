/*
 * Copyright 2024 HM Revenue & Customs
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

import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support.{PlatformAnalyticsStubs, StartAndStopWireMock, TestDataSupport, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItIdType
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.time.LocalDateTime
import scala.concurrent.Future
import scala.concurrent.duration._

class PlatformAnalyticsServiceISpec
    extends UnitSpec with PlatformAnalyticsStubs with GuiceOneServerPerSuite with TestDataSupport with StartAndStopWireMock
    with CleanMongoCollectionSupport {

  def commonStubs() = {}

  implicit override lazy val app: Application = new GuiceApplicationBuilder()
    .configure(
      "mongodb.uri"                                   -> mongoUri,
      "microservice.services.platform-analytics.port" -> wiremockPort
    )
    .build()

  private val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  override def beforeEach(): Unit =
    super.beforeEach()

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  val intervalMillis = appConfig.invitationUpdateStatusInterval.seconds.toMillis
  val batchSize = appConfig.gaBatchSize

  val now = LocalDateTime.now()

  val itsaClient2 = TestClient(personal, "Trade Apples", MtdIt, MtdItIdType, "MTDITID", mtdItId2, nino2, mtdItId)
  val itsaClient3 = TestClient(personal, "Trade Bananas", MtdIt, MtdItIdType, "MTDITID", mtdItId3, nino3, mtdItId2)
  val itsaClient4 = TestClient(personal, "Trade Mangos", MtdIt, MtdItIdType, "MTDITID", mtdItId4, nino4, mtdItId3)
  val itsaClient5 = TestClient(personal, "Trade Pineapple", MtdIt, MtdItIdType, "MTDITID", mtdItId5, nino5, mtdItId4)
  val itsaClient6 = TestClient(personal, "Trade Peppers", MtdIt, MtdItIdType, "MTDITID", mtdItId6, nino6, mtdItId5)

  val testClients = List(
    itsaClient,
    itsaClient2,
    itsaClient3,
    itsaClient4,
    itsaClient5,
    itsaClient6
  )

  def createInvitation(testClient: TestClient[_]): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      None,
      Some("origin header")
    )

  trait TestSetup {
    testClients.foreach(client => await(createInvitation(client)))
  }

}
