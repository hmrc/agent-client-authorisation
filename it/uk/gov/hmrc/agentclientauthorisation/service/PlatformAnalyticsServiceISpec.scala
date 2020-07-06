package uk.gov.hmrc.agentclientauthorisation.service

import org.joda.time.DateTime
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.PlatformAnalyticsConnector
import uk.gov.hmrc.agentclientauthorisation.model.Service.MtdIt
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Invitation, MtdItIdType}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, MongoAppAndStubs, PlatformAnalyticsStubs, TestDataSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class PlatformAnalyticsServiceISpec extends UnitSpec with MongoAppAndStubs with MongoApp with PlatformAnalyticsStubs with GuiceOneServerPerSuite with TestDataSupport {

  private lazy val invitationsRepo =
    app.injector.instanceOf[InvitationsRepositoryImpl]

  override implicit val patienceConfig = PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val analyticsConnector = app.injector.instanceOf[PlatformAnalyticsConnector]

  val intervalMillis = appConfig.invitationUpdateStatusInterval.seconds.toMillis
  val batchSize = appConfig.gaBatchSize

  private lazy val service = new PlatformAnalyticsService(
    invitationsRepo,
    analyticsConnector,
    appConfig
  )

  private def expireInvitations(i:List[Invitation], recently: Boolean): List[String] = {
    Future.sequence(i.map(
      x => await(invitationsRepo.update(
        x,Expired, if(recently) now.minusMillis(500) else now.minusDays(1)))
        .map(_.invitationId.value)))
  }

  val now = DateTime.now()

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

  def createInvitation(testClient: TestClient[_]): Future[Invitation] = {
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      None,
      now.minusDays(14),
      now.toLocalDate,
      Some("origin header"))
  }

  trait TestSetup {
    testClients.foreach(client => await(createInvitation(client)))
  }

  "PlatFormAnalyticsService" should {

    "find invitations that may have expired only since the last update scheduler and send" in new TestSetup {

      val all = await(invitationsRepo.findAll())
      expireInvitations(all.take(1), false)
      val ids = expireInvitations(all.drop(1), true)

      givenPlatformAnalyticsRequestSent(false)
      givenPlatformAnalyticsRequestSent(false)
      givenPlatformAnalyticsRequestSent(true)

      val result = await(service.reportExpiredInvitations())

      result shouldBe (())

      verifyAnalyticsRequestSent(3)

    }
  }

  "not find any invitations if they expired before the last update scheduler run" in new TestSetup {

    val all = await(invitationsRepo.findAll())
    expireInvitations(all, false)

    val result = await(service.reportExpiredInvitations())

    result shouldBe (())

    verifyAnalyticsRequestWasNotSent()

  }
}
