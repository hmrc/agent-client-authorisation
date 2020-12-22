package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.ActorSystem
import org.joda.time.DateTime
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.{DetailsForEmail, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, MongoAppAndStubs}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class RemovePersonalInfoSchedulerISpec extends UnitSpec with MongoAppAndStubs with MongoApp with GuiceOneServerPerSuite {

  private lazy val invitationsRepo = app.injector.instanceOf[InvitationsRepositoryImpl]
  val appConfig = app.injector.instanceOf[AppConfig]

  val scheduler = new RemovePersonalInfoScheduler(
    app.injector.instanceOf[ScheduleRepository],
    app.injector.instanceOf[InvitationsService],
    app.injector.instanceOf[ActorSystem],
    appConfig
  )

  override implicit val patienceConfig =
    PatienceConfig(scaled(Span(60, Seconds)), scaled(Span(5, Seconds)))

  "RemovePersonalInfoScheduler" should {

    val arn = Arn("AARN0000057")

    "remove personal informatino after they are expired" in {
      val now = DateTime.now()
      val expiredInvitations: Seq[Invitation] = (1 to 5).map(
        i =>
          Invitation.createNew(
            arn,
            None,
            Service.MtdIt,
            MtdItId(s"AB${i}23456B"),
            MtdItId(s"AB${i}23456A"),
            Some(DetailsForEmail(s"mustDelete@x.com", s"mustDelete", s"mustDelete")),
            now.minusDays(appConfig.removePersonalInfoExpiryDuration.toDays.toInt + Random.nextInt(15) + 2),
            now.minusDays(Random.nextInt(5) + 1).toLocalDate,
            None
        ))

      val activeInvitations: Seq[Invitation] = (1 to 5).map(
        i =>
          Invitation.createNew(
            arn,
            None,
            Service.MtdIt,
            MtdItId(s"AB${i}23456B"),
            MtdItId(s"AB${i}23456A"),
            Some(DetailsForEmail(s"active@x.com", s"active", s"active")),
            now.minusDays(Random.nextInt(15)),
            now.minusDays(Random.nextInt(5)).toLocalDate,
            None
        ))

      await(Future.sequence(expiredInvitations.map(invitationsRepo.insert)))
      await(Future.sequence(activeInvitations.map(invitationsRepo.insert)))

      await(invitationsRepo.findAll()).length shouldBe 10

      eventually {
          val remainingInvitations = await(invitationsRepo.findAll())
          remainingInvitations.length shouldBe 10
          remainingInvitations.filter(_.detailsForEmail.isEmpty).length shouldBe 5
          remainingInvitations.filter(_.detailsForEmail.isDefined).length shouldBe 5
          remainingInvitations.flatMap(_.detailsForEmail).map(_.agencyName).foreach(_ shouldBe "active")
      }
    }
  }
}
