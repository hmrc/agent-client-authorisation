package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.ActorSystem
import org.joda.time.DateTime
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, MongoAppAndStubs}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class InvitationsStatusUpdateSchedulerISpec
    extends UnitSpec with MongoAppAndStubs with MongoApp with GuiceOneServerPerSuite {

  private lazy val invitationsRepo =
    app.injector.instanceOf[InvitationsRepositoryImpl]

  val scheduler = new InvitationsStatusUpdateScheduler(
    app.injector.instanceOf[ScheduleRepository],
    app.injector.instanceOf[InvitationsService],
    app.injector.instanceOf[PlatformAnalyticsService],
    app.injector.instanceOf[ActorSystem],
    app.injector.instanceOf[AppConfig]
  )

  override implicit val patienceConfig =
    PatienceConfig(scaled(Span(60, Seconds)), scaled(Span(5, Seconds)))

  "InvitationsStatusUpdateScheduler" should {

    val arn = Arn("AARN0000002")

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

      await(Future.sequence(expiredInvitations.map(invitationsRepo.insert)))
      await(Future.sequence(activeInvitations.map(invitationsRepo.insert)))

      await(invitationsRepo.findAll()).length shouldBe 10

      eventually {
        await(invitationsRepo.findInvitationsBy(status = Some(Expired))).length shouldBe 5
      }
    }
  }
}
