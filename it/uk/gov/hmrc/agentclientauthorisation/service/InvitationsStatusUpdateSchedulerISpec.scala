package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.kenshoo.play.metrics.Metrics
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
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

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class InvitationsStatusUpdateSchedulerISpec
    extends TestKit(ActorSystem("testSystem")) with UnitSpec with Eventually with IntegrationPatience with CleanMongoCollectionSupport {

  implicit lazy val app = new GuiceApplicationBuilder()
    .configure("mongodb.uri" -> mongoUri,
    "metrics.jvm" -> false,
    "metrics.enabled" -> false,
    "auditing.enabled" -> false
    )
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
    metrics)

  val arn = Arn("AARN0000002")

  val testKit = ActorTestKit()

  val actorRef = system.actorOf(Props(new TaskActor(schedulerRepository, invitationsService, 60)))

  "InvitationsStatusUpdateScheduler" should {

    "update status of all Pending invitations to Expired if they are expired" in {

      val now = LocalDateTime.now()

      val expiredInvitations: Seq[Invitation] = for (i <- 1 to 5)
        yield
          Invitation.createNew(
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

      val activeInvitations: Seq[Invitation] = for (i <- 1 to 5)
        yield
          Invitation.createNew(
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

      await(schedulerRepository.collection.insertOne(
        ScheduleRecord("uid", now.minusSeconds(2), InvitationExpired)).toFuture())

      await(Future.sequence(expiredInvitations.map(invitationsRepository.collection.insertOne(_).toFuture())))
      await(Future.sequence(activeInvitations.map(invitationsRepository.collection.insertOne(_).toFuture())))

      await(invitationsRepository.collection.find().toFuture()).length shouldBe 10

      testKit.scheduler.scheduleOnce(5.millis, new Runnable { def run = { actorRef ! "uid" }} )

      eventually {
        await(invitationsRepository.findInvitationsBy(status = Some(Expired))).length shouldBe 5
      }
      testKit.shutdownTestKit()
    }
  }
}
