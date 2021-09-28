package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.{ActorSystem, Props}
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.testkit.TestKit
import com.kenshoo.play.metrics.Metrics
import org.joda.time.DateTime
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoScheduleRepository, ScheduleRecord, SchedulerType}
import uk.gov.hmrc.agentclientauthorisation.support.MongoAppAndStubs
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class InvitationsStatusUpdateSchedulerISpec
    extends TestKit(ActorSystem("testSystem")) with UnitSpec with MongoAppAndStubs {

  val relationshipConnector = app.injector.instanceOf[RelationshipsConnector]
  val analyticsService = app.injector.instanceOf[PlatformAnalyticsService]
  val desConnector = app.injector.instanceOf[DesConnector]
  val appConfig = app.injector.instanceOf[AppConfig]
  val emailService = app.injector.instanceOf[EmailService]
  val metrics = app.injector.instanceOf[Metrics]
  val schedulerRepository = app.injector.instanceOf[MongoScheduleRepository]
  val invitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]

  val invitationsService = new InvitationsService(
    invitationsRepository,
    relationshipConnector,
    analyticsService,
    desConnector,
    emailService,
    appConfig,
    metrics)

  val arn = Arn("AARN0000002")

  val testKit = ActorTestKit()

  val actorRef = system.actorOf(Props(new TaskActor(schedulerRepository,invitationsService, analyticsService, 60)))

  "InvitationsStatusUpdateScheduler" should {

    "update status of all Pending invitations to Expired if they are expired" in {

      val now = DateTime.now()

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
            MtdItId(s"AB${i}23456B"),
            MtdItId(s"AB${i}23456A"),
            None,
            now.plusDays(Random.nextInt(15)),
            now.plusDays(Random.nextInt(5)).toLocalDate,
            None
          )

      await(schedulerRepository.insert(ScheduleRecord("uid", now.minusSeconds(2), SchedulerType.InvitationExpired)))

      await(Future.sequence(expiredInvitations.map(invitationsRepository.insert)))
      await(Future.sequence(activeInvitations.map(invitationsRepository.insert)))

      await(invitationsRepository.findAll()).length shouldBe 10

      testKit.scheduler.scheduleOnce(2.seconds, new Runnable { def run = { actorRef ! "uid" }} )

      eventually {
        await(invitationsRepository.findInvitationsBy(status = Some(Expired))).length shouldBe 5
      }
      testKit.shutdownTestKit()
    }
  }
}
