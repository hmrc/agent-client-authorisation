package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.{ActorSystem, Props}
import akka.testkit.TestKit
import com.github.tomakehurst.wiremock.client.WireMock.{postRequestedFor, urlPathEqualTo, verify}
import com.kenshoo.play.metrics.Metrics
import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoScheduleRepository, ScheduleRecord, SchedulerType}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.arn
import uk.gov.hmrc.agentclientauthorisation.support.{EmailStub, MongoAppAndStubs}
import uk.gov.hmrc.agentclientauthorisation.util.DateUtils
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import play.api.test.Helpers._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random

class RoutineJobSchedulerISpec extends TestKit(ActorSystem("testSystem")) with UnitSpec with MongoAppAndStubs  with EmailStub {

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

  val testKit = ActorTestKit()


  val actorRef = system.actorOf(Props(new RoutineScheduledJobActor(schedulerRepository,invitationsService, 60)))

  "RoutineJobScheduler" should {

    "run scheduled tasks" in {

      val now = DateTime.now(DateTimeZone.UTC)

      await(schedulerRepository.insert(ScheduleRecord("uid", now.minusSeconds(2), SchedulerType.RemovePersonalInfo)))

      val pendingInvitation = Invitation.createNew(
        arn = Arn(arn),
        clientType = None,
        service = Service.MtdIt,
        clientId = MtdItId(s"AB123456B"),
        suppliedClientId = MtdItId(s"AB123456A"),
        detailsForEmail = Some(DetailsForEmail("keep@x.com", "keep", "keep")),
        startDate = now.minusDays(1),
        expiryDate = now.plusDays(20).toLocalDate,
        origin = None
      )

      val toRemoveDetailsForEmail = Invitation.createNew(
        arn = Arn(arn),
        clientType = None,
        service = Service.MtdIt,
        clientId = MtdItId(s"AB223456B"),
        suppliedClientId = MtdItId(s"AB223456A"),
        detailsForEmail = Some(DetailsForEmail("mustDelete@x.com", "mustDelete", "mustDelete")),
        startDate = now.minusDays(appConfig.removePersonalInfoExpiryDuration.toDays.toInt + Random.nextInt(15) + 2),
        expiryDate = now.minusDays(Random.nextInt(5) + 1).toLocalDate,
        origin = None
      )

      val newPartialAuthInvitation = Invitation.createNew(
        arn = Arn(arn),
        clientType = None,
        service = Service.MtdIt,
        clientId = MtdItId(s"AB323456B"),
        suppliedClientId = MtdItId(s"AB123456A"),
        detailsForEmail = Some(DetailsForEmail("keep@x.com", "keep", "keep")),
        startDate = now.minusDays(21),
        expiryDate = now.toLocalDate,
        origin = None
      )

      val oldPartialAuthInvitation = Invitation.createNew(
        arn = Arn(arn),
        clientType = None,
        service = Service.MtdIt,
        clientId = MtdItId(s"AB423456B"),
        suppliedClientId = MtdItId(s"AB223456A"),
        detailsForEmail = None,
        startDate = now.minusDays(appConfig.altItsaExpiryDays + 21),
        expiryDate = now.minusDays(appConfig.altItsaExpiryDays).toLocalDate,
        origin = None
      )

      val aboutToExpireInvitation = Invitation.createNew(
        arn = Arn(arn),
        clientType = None,
        service = Service.MtdIt,
        clientId = MtdItId(s"AB223456B"),
        suppliedClientId = MtdItId(s"AB223456A"),
        detailsForEmail =  Some(DetailsForEmail("agency@x.com", "MyAgency", "clientName")),
        startDate = now.minusDays(appConfig.invitationExpiringDuration.toDays.toInt + appConfig.sendEmailPriorToExpireDays),
        expiryDate = now.plusDays(appConfig.sendEmailPriorToExpireDays).toLocalDate,
        origin = None
      )

      await(Future.sequence(List(
        pendingInvitation,
        toRemoveDetailsForEmail,
        newPartialAuthInvitation,
        oldPartialAuthInvitation,
        aboutToExpireInvitation)
        .map(invitationsRepository.insert)))


      await(invitationsRepository.update(newPartialAuthInvitation,PartialAuth, now.minusDays(5)))
      await(invitationsRepository.update(oldPartialAuthInvitation, PartialAuth, now.minusDays(appConfig.altItsaExpiryDays + 5)))

      await(invitationsRepository.findByInvitationId(pendingInvitation.invitationId)).head.detailsForEmail.isDefined shouldBe true
      await(invitationsRepository.findByInvitationId(toRemoveDetailsForEmail.invitationId)).head.detailsForEmail.isDefined shouldBe true

      givenEmailSent(EmailInformation(
        to = Seq("agency@x.com"),
        templateId = "agent_invitation_about_to_expire_single",
        parameters = Map(
          "agencyName"          -> "MyAgency",
          "numberOfInvitations" -> "1",
          "createdDate"         -> DateUtils.displayDate(aboutToExpireInvitation.mostRecentEvent().time.toLocalDate),
          "expiryDate"          -> DateUtils.displayDate(aboutToExpireInvitation.expiryDate)
        )))

      testKit.scheduler.scheduleOnce(2.seconds, new Runnable { def run = { actorRef ! "uid" }} )

      eventually {
        await(invitationsRepository.findByInvitationId(newPartialAuthInvitation.invitationId)).head.status shouldBe PartialAuth

        await(invitationsRepository.findByInvitationId(oldPartialAuthInvitation.invitationId)).head.status shouldBe DeAuthorised
        await(invitationsRepository.findByInvitationId(oldPartialAuthInvitation.invitationId)).head.isRelationshipEnded shouldBe true
        await(invitationsRepository.findByInvitationId(oldPartialAuthInvitation.invitationId)).head.relationshipEndedBy shouldBe Some("HMRC")

      }

      eventually {

        verify(
          1,
          postRequestedFor(urlPathEqualTo("/hmrc/email"))
        )

        await(invitationsRepository.findByInvitationId(toRemoveDetailsForEmail.invitationId)).head.detailsForEmail shouldBe None
        await(invitationsRepository.findByInvitationId(pendingInvitation.invitationId)).head.detailsForEmail shouldBe Some(DetailsForEmail(s"keep@x.com", s"keep", s"keep"))
      }
      testKit.shutdownTestKit()
    }
  }
}
