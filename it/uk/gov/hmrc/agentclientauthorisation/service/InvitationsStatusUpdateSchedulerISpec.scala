package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.ActorSystem
import org.joda.time.DateTime
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, MongoAppAndStubs}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random

class InvitationsStatusUpdateSchedulerISpec
    extends UnitSpec with MongoAppAndStubs with MongoApp with OneServerPerSuite {

  private lazy val invitationsRepo =
    app.injector.instanceOf[InvitationsRepositoryImpl]

  val scheduler = new InvitationsStatusUpdateScheduler(
    app.injector.instanceOf[ScheduleRepository],
    app.injector.instanceOf[InvitationsService],
    app.injector.instanceOf[ActorSystem],
    1,
    true
  )

  override implicit val patienceConfig =
    PatienceConfig(scaled(Span(30, Seconds)), scaled(Span(2, Seconds)))

  "InvitationsStatusUpdateScheduler" should {

    val arn = Arn("AARN0000002")
    val mtdItId = MtdItId("ABCDEF123456789")
    val nino = Nino("AB123456C")
    val mtdItIdType = "MTDITID"

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
            now.minusDays(Random.nextInt(5) + 1).toLocalDate
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
            now.plusDays(Random.nextInt(5)).toLocalDate
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
