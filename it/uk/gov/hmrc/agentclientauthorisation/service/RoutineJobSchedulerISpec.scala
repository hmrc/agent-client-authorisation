package uk.gov.hmrc.agentclientauthorisation.service


import akka.actor.ActorSystem
import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlPathEqualTo, verify}
import org.joda.time.DateTime
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model.{DetailsForEmail, EmailInformation, Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{EmailStub, MongoApp, MongoAppAndStubs}
import uk.gov.hmrc.agentclientauthorisation.util.DateUtils
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Urn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Random


class RoutineJobSchedulerISpec extends UnitSpec with MongoAppAndStubs with MongoApp with EmailStub with GuiceOneServerPerSuite {

  private lazy val invitationsRepo = app.injector.instanceOf[InvitationsRepositoryImpl]
  val appConfig = app.injector.instanceOf[AppConfig]

  val scheduler = new RoutineJobScheduler(
    app.injector.instanceOf[ScheduleRepository],
    app.injector.instanceOf[InvitationsService],
    app.injector.instanceOf[ActorSystem],
    appConfig
  )

  override implicit val patienceConfig =
    PatienceConfig(scaled(Span(60, Seconds)), scaled(Span(5, Seconds)))

  "RoutineJobScheduler - remove personal info job" should {

    val arn = Arn("AARN0000057")

    "remove personal information after they are expired" in {
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

  "RoutineJobScheduler - send warning email job" should {

    "send email to agents for invitations that are about to expire" in {

      val arn1 = Arn("AARN0876516")
      val arn2 = Arn("CARN2752751")
      val arn3 = Arn("EARN2086970")

      val arn = Array(arn1, arn2, arn3)
      val now = DateTime.now()
      val expireIn5DaysMtdIt: Seq[Invitation] = (1 to 3).map(
        i =>
          Invitation.createNew(
            arn(i-1),
            None,
            Service.MtdIt,
            MtdItId(s"JH${i}23456B"),
            MtdItId(s"JH${i}23456A"),
            Some(DetailsForEmail(s"mustDelete${i}@x.com", s"mustDelete${i}", s"mustDelete${i}")),
            now.minusDays(16),
            now.plusDays(5).toLocalDate,
            None
          )
      )

      val expireIn5DaysIRV: Seq[Invitation] = (1 to 3).map(
        i =>
        Invitation.createNew(
          arn(i-1),
          None,
          Service.PersonalIncomeRecord,
          Nino(s"JH${i}23456B"),
          Nino(s"JH${i}23456A"),
          Some(DetailsForEmail(s"mustDeleteIrv${i}@x.com", s"mustDeleteIrv${i}", s"mustDeleteIrv${i}")),
          now.minusDays(16),
          now.plusDays(5).toLocalDate,
          None
        )
      )

      val pendingInvitations: Seq[Invitation] = (1 to 3).map(
        i =>
          Invitation.createNew(
            arn(i-1),
            None,
            Service.TrustNT,
            Urn(s"TRUSTNT${i}2345678"),
            Urn(s"TRUSTNT${i}2345678"),
            Some(DetailsForEmail(s"active${i}@x.com", s"active${i}", s"active${i}")),
            now.minusDays(10),
            now.plusDays(11).toLocalDate,
            None
          ))

      (1 to 3).map(
        i =>
          givenEmailSent(EmailInformation(
            to = Seq(s"mustDelete${i}@x.com"),
            templateId = "agent_invitations_about_to_expire",
            parameters = Map(
              "agencyName" -> s"mustDelete${i}",
              "numberOfInvitations" -> "2",
              "createdDate" -> DateUtils.displayDate(now.minusDays(16).toLocalDate),
              "expiryDate" -> DateUtils.displayDate(now.plusDays(5).toLocalDate)
            )))
      )

      await(Future.sequence(expireIn5DaysMtdIt.map(invitationsRepo.insert)))
      await(Future.sequence(expireIn5DaysIRV.map(invitationsRepo.insert)))
      await(Future.sequence(pendingInvitations.map(invitationsRepo.insert)))

      await(invitationsRepo.findAll()).length shouldBe 9

      eventually {
        val remainingInvitations = await(invitationsRepo.findAll())
        remainingInvitations.length shouldBe 9
        remainingInvitations.filter(_.detailsForEmail.isEmpty).length shouldBe 0
        remainingInvitations.filter(_.detailsForEmail.isDefined).length shouldBe 9
      }
      eventually {
        verify(
          3,
          postRequestedFor(urlPathEqualTo("/hmrc/email"))
        )
      }
    }
  }
}
