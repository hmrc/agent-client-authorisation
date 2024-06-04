package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlPathEqualTo, verify}
import com.kenshoo.play.metrics.Metrics
import org.scalatest.concurrent.IntegrationPatience
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.audit.{AgentClientInvitationEvent, AuditService}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, IfConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoScheduleRepository, RemovePersonalInfo, ScheduleRecord}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{EmailStub, MongoAppAndStubs, UnitSpec}
import uk.gov.hmrc.agentclientauthorisation.util.DateUtils
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Service}
import uk.gov.hmrc.domain.Nino

import java.time.LocalDateTime
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class RoutineJobSchedulerISpec extends TestKit(ActorSystem("testSystem"))
  with UnitSpec with MongoAppAndStubs with EmailStub with IntegrationPatience {

  val relationshipConnector = app.injector.instanceOf[RelationshipsConnector]
  val analyticsService = app.injector.instanceOf[PlatformAnalyticsService]
  val desConnector = app.injector.instanceOf[DesConnector]
  val ifConnector = app.injector.instanceOf[IfConnector]
  val appConfig = app.injector.instanceOf[AppConfig]
  val emailService = app.injector.instanceOf[EmailService]
  val schedulerRepository = app.injector.instanceOf[MongoScheduleRepository]
  val invitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
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

  "RoutineJobScheduler" should {

    "remove the details for email for each invitation where the period for retaining these has expired" in {
      val testKit = ActorTestKit()
      val actorRef = system.actorOf(Props(new RoutineScheduledJobActor(schedulerRepository, invitationsService, repeatInterval = 60, altItsaExpiryEnable = true)))

      val now = LocalDateTime.now()

      await(schedulerRepository.collection.insertOne(ScheduleRecord("uid", now.minusSeconds(2), RemovePersonalInfo)).toFuture())

      val pendingInvitation = Invitation.createNew(
        arn = Arn(arn),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289806"),
        suppliedClientId = Nino(s"AB123456A"),
        detailsForEmail = Some(DetailsForEmail("name@example.com", "ABC Agents", "Client X")),
        startDate = now.minusDays(1),
        expiryDate = now.plusDays(20).toLocalDate,
        origin = None
      )

      val pastPeriodOfRemoval1 = Invitation.createNew(
        arn = Arn(arn2),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289806"),
        suppliedClientId = Nino(s"AB123456A"),
        detailsForEmail = Some(DetailsForEmail("name@example.com", "ABC Agents", "Client X")),
        startDate = now.minusDays(appConfig.removePersonalInfoExpiryDuration.toDays.toInt + Random.nextInt(15) + 2),
        expiryDate = now.minusDays(appConfig.removePersonalInfoExpiryDuration.toDays.toInt + Random.nextInt(15) + 2).plusDays(21).toLocalDate,
        origin = None
      )

      await(Future sequence List(pendingInvitation, pastPeriodOfRemoval1).map(invitationsRepository.collection.insertOne(_).toFuture()))

      await(invitationsRepository.findByInvitationId(pendingInvitation.invitationId)).head.detailsForEmail.isDefined shouldBe true
      await(invitationsRepository.findByInvitationId(pastPeriodOfRemoval1.invitationId)).head.detailsForEmail.isDefined shouldBe true

      testKit.scheduler.scheduleOnce(0.seconds, new Runnable {
        def run = {
          actorRef ! "uid"
        }
      })

      eventually {

        await(invitationsRepository.findByInvitationId(pendingInvitation.invitationId)).head.detailsForEmail.isDefined shouldBe true
        await(invitationsRepository.findByInvitationId(pastPeriodOfRemoval1.invitationId)).head.detailsForEmail.isDefined shouldBe false
      }

      testKit.shutdownTestKit()
    }

    "find invitations that are about to expire and send one email per agent" in {

      val testKit = ActorTestKit()
      val actorRef = system.actorOf(Props(new RoutineScheduledJobActor(schedulerRepository, invitationsService, repeatInterval = 60, altItsaExpiryEnable = true)))

      val now = LocalDateTime.now()

      await(schedulerRepository.collection.insertOne(ScheduleRecord("uid", now.minusSeconds(2), RemovePersonalInfo)).toFuture())

      val aboutToExpireInvitation1 = Invitation.createNew(
        arn = Arn(arn),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289806"),
        suppliedClientId = Nino(s"AB123456A"),
        detailsForEmail = Some(DetailsForEmail("name@example.com", "ABC Agents", "Client X")),
        startDate = now.minusDays(appConfig.invitationExpiringDuration.toDays.toInt + appConfig.sendEmailPriorToExpireDays),
        expiryDate = now.plusDays(appConfig.sendEmailPriorToExpireDays).toLocalDate,
        origin = None
      )

      val aboutToExpireInvitation2 = Invitation.createNew(
        arn = Arn(arn),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289807"),
        suppliedClientId = Nino(s"AB123456B"),
        detailsForEmail = Some(DetailsForEmail("name@example.com", "ABC Agents", "Client Y")),
        startDate = now.minusDays(appConfig.invitationExpiringDuration.toDays.toInt + appConfig.sendEmailPriorToExpireDays),
        expiryDate = now.plusDays(appConfig.sendEmailPriorToExpireDays).toLocalDate,
        origin = None
      )

      val aboutToExpireInvitation3 = Invitation.createNew(
        arn = Arn(arn2),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289808"),
        suppliedClientId = Nino(s"AB123456C"),
        detailsForEmail = Some(DetailsForEmail("other@example.com", "DEF Agents", "Client Z")),
        startDate = now.minusDays(appConfig.invitationExpiringDuration.toDays.toInt + appConfig.sendEmailPriorToExpireDays),
        expiryDate = now.plusDays(appConfig.sendEmailPriorToExpireDays).toLocalDate,
        origin = None
      )

      await(Future.sequence(List(aboutToExpireInvitation1, aboutToExpireInvitation2, aboutToExpireInvitation3)
        .map(invitationsRepository.collection.insertOne(_).toFuture())))

      givenEmailSent(EmailInformation(
        to = Seq("name@example.com"),
        templateId = "agent_invitations_about_to_expire",
        parameters = Map(
          "agencyName" -> "ABC Agents",
          "numberOfInvitations" -> "2",
          "createdDate" -> DateUtils.displayDate(aboutToExpireInvitation1.mostRecentEvent().time.toLocalDate),
          "expiryDate" -> DateUtils.displayDate(aboutToExpireInvitation1.expiryDate)
        )))

      givenEmailSent(EmailInformation(
        to = Seq("other@example.com"),
        templateId = "agent_invitation_about_to_expire_single",
        parameters = Map(
          "agencyName" -> "DEF Agents",
          "numberOfInvitations" -> "1",
          "createdDate" -> DateUtils.displayDate(aboutToExpireInvitation1.mostRecentEvent().time.toLocalDate),
          "expiryDate" -> DateUtils.displayDate(aboutToExpireInvitation1.expiryDate)
        )))

      testKit.scheduler.scheduleOnce(2.seconds, new Runnable {
        def run = {
          actorRef ! "uid"
        }
      })

      eventually {
        verify(
          2,
          postRequestedFor(urlPathEqualTo("/hmrc/email"))
        )
      }
      testKit.shutdownTestKit()
    }

    "cancel partialAuth invitations after the partial-auth expiry period has elapsed and send an audit event for each one" in {

      val testKit = ActorTestKit()
      val actorRef = system.actorOf(Props(new RoutineScheduledJobActor(schedulerRepository, invitationsService, repeatInterval = 60, altItsaExpiryEnable = true)))

      val now = LocalDateTime.now()

      await(schedulerRepository.collection.insertOne(ScheduleRecord("uid", now.minusSeconds(2), RemovePersonalInfo)).toFuture())

      val partialAuthInvitation1 = Invitation.createNew(
        arn = Arn(arn),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289806"),
        suppliedClientId = Nino(s"AB123456A"),
        detailsForEmail = Some(DetailsForEmail("name@example.com", "ABC Agents", "Client X")),
        startDate = now.minusDays(appConfig.altItsaExpiryDays + 4),
        expiryDate = now.minusDays(appConfig.altItsaExpiryDays).plusDays(17).toLocalDate,
        origin = None
      )

      val partialAuthInvitation2 = Invitation.createNew(
        arn = Arn(arn),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289807"),
        suppliedClientId = Nino(s"AB123456B"),
        detailsForEmail = Some(DetailsForEmail("name@example.com", "ABC Agents", "Client Y")),
        startDate = now.minusDays(appConfig.altItsaExpiryDays + 10),
        expiryDate = now.minusDays(appConfig.altItsaExpiryDays).plusDays(11).toLocalDate,
        origin = None
      )

      val partialAuthInvitation3 = Invitation.createNew(
        arn = Arn(arn2),
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = MtdItId(s"VDBS44578289808"),
        suppliedClientId = Nino(s"AB123456C"),
        detailsForEmail = Some(DetailsForEmail("other@example.com", "DEF Agents", "Client Z")),
        startDate = now.minusDays(appConfig.altItsaExpiryDays - 2),
        expiryDate = now.minusDays(appConfig.altItsaExpiryDays - 2).plusDays(21).toLocalDate,
        origin = None
      )

      await(Future.sequence(List(partialAuthInvitation1, partialAuthInvitation2, partialAuthInvitation3)
        .map(invitationsRepository.collection.insertOne(_).toFuture())))

      await(invitationsRepository.update(partialAuthInvitation1, PartialAuth, now.minusDays(appConfig.altItsaExpiryDays + 2)))
      await(invitationsRepository.update(partialAuthInvitation2, PartialAuth, now.minusDays(appConfig.altItsaExpiryDays + 7)))
      await(invitationsRepository.update(partialAuthInvitation3, PartialAuth, now.minusDays(appConfig.altItsaExpiryDays - 1)))

      await(invitationsRepository.findByInvitationId(partialAuthInvitation1.invitationId)).head.status shouldBe PartialAuth
      await(invitationsRepository.findByInvitationId(partialAuthInvitation2.invitationId)).head.status shouldBe PartialAuth
      await(invitationsRepository.findByInvitationId(partialAuthInvitation3.invitationId)).head.status shouldBe PartialAuth


      testKit.scheduler.scheduleOnce(2.seconds, new Runnable {
        def run = {
          actorRef ! "uid"
        }
      })

      eventually {
        await(invitationsRepository.findByInvitationId(partialAuthInvitation1.invitationId)).head.status shouldBe DeAuthorised
        await(invitationsRepository.findByInvitationId(partialAuthInvitation2.invitationId)).head.status shouldBe DeAuthorised
        await(invitationsRepository.findByInvitationId(partialAuthInvitation3.invitationId)).head.status shouldBe PartialAuth
        verifyAuditRequestSent(1,
          AgentClientInvitationEvent.HmrcExpiredAgentServiceAuthorisation,
          Map(
            "transactionName" -> "hmrc expired agent:service authorisation",
            "path" -> ""),
          Map(
            "clientNINO" -> partialAuthInvitation1.suppliedClientId.toString,
            "service" -> partialAuthInvitation1.service.toString,
            "agentReferenceNumber" -> partialAuthInvitation1.arn.value,
            "deleteStatus" -> "success")
        )

        verifyAuditRequestSent(1,
          AgentClientInvitationEvent.HmrcExpiredAgentServiceAuthorisation,
          Map(
            "transactionName" -> "hmrc expired agent:service authorisation",
            "path" -> ""),
          Map(
            "clientNINO" -> partialAuthInvitation2.suppliedClientId.toString,
            "service" -> partialAuthInvitation2.service.toString,
            "agentReferenceNumber" -> partialAuthInvitation2.arn.value,
            "deleteStatus" -> "success")
        )
      }
      testKit.shutdownTestKit()
    }
  }
}
