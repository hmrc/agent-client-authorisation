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

import org.apache.pekko.actor.testkit.typed.scaladsl.ActorTestKit
import org.apache.pekko.actor.{ActorSystem, Props}
import org.apache.pekko.testkit.TestKit
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, IfConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Invitation}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationExpired, InvitationsRepositoryImpl, MongoScheduleRepository, ScheduleRecord}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Service}
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class InvitationsStatusUpdateSchedulerISpec
    extends TestKit(ActorSystem("testSystem")) with UnitSpec with Eventually with IntegrationPatience with CleanMongoCollectionSupport {

  implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> mongoUri, "metrics.jvm" -> false, "metrics.enabled" -> false, "auditing.enabled" -> false)
    .build()

  val relationshipConnector = app.injector.instanceOf[RelationshipsConnector]
  val analyticsService = app.injector.instanceOf[PlatformAnalyticsService]
  val desConnector = app.injector.instanceOf[DesConnector]
  val ifConnector = app.injector.instanceOf[IfConnector]
  val appConfig = app.injector.instanceOf[AppConfig]
  val emailService = app.injector.instanceOf[EmailService]
  val metrics = app.injector.instanceOf[Metrics]
  val schedulerRepository = new MongoScheduleRepository(mongoComponent)
  val invitationsRepository = new InvitationsRepositoryImpl(mongoComponent, metrics)
  val auditService = app.injector.instanceOf(classOf[AuditService])

  val invitationsService = new InvitationsService(
    invitationsRepository,
    relationshipConnector,
    analyticsService,
    desConnector,
    ifConnector,
    emailService,
    auditService,
    appConfig,
    metrics
  )

  val arn = Arn("AARN0000002")

  val testKit = ActorTestKit()

  val actorRef = system.actorOf(Props(new TaskActor(schedulerRepository, invitationsService, 60)))

  "InvitationsStatusUpdateScheduler" should {

    "update status of all Pending invitations to Expired if they are expired" in {

      val now = LocalDateTime.now()

      val expiredInvitations: Seq[Invitation] =
        for (i <- 1 to 5)
          yield Invitation.createNew(
            arn,
            None,
            Service.MtdIt,
            MtdItId(s"AB${i}23456B"),
            MtdItId(s"AB${i}23456A"),
            None,
            now.minusDays(Random.nextInt(15)),
            now.minusDays(Random.nextInt(5) + 1).toLocalDate,
            None
          )

      val activeInvitations: Seq[Invitation] =
        for (i <- 1 to 5)
          yield Invitation.createNew(
            arn,
            None,
            Service.MtdIt,
            MtdItId(s"AB${i}33456B"),
            MtdItId(s"AB${i}33456A"),
            None,
            now.plusDays(Random.nextInt(15)),
            now.plusDays(Random.nextInt(5)).toLocalDate,
            None
          )

      await(schedulerRepository.collection.insertOne(ScheduleRecord("uid", now.minusSeconds(2), InvitationExpired)).toFuture())

      await(Future.sequence(expiredInvitations.map(invitationsRepository.collection.insertOne(_).toFuture())))
      await(Future.sequence(activeInvitations.map(invitationsRepository.collection.insertOne(_).toFuture())))

      await(invitationsRepository.collection.find().toFuture()).length shouldBe 10

      testKit.scheduler.scheduleOnce(5.millis, new Runnable { def run = actorRef ! "uid" })

      eventually {
        await(invitationsRepository.findInvitationsBy(status = Some(Expired), within30Days = appConfig.acrMongoActivated)).length shouldBe 5
      }
      testKit.shutdownTestKit()
    }
  }
}
