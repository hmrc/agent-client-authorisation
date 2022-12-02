package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.ActorSystem
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.PlatformAnalyticsConnector
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Invitation}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support.{PlatformAnalyticsStubs, StartAndStopWireMock, TestDataSupport, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItIdType
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class PlatformAnalyticsServiceISpec extends UnitSpec with PlatformAnalyticsStubs with GuiceOneServerPerSuite with TestDataSupport
   with StartAndStopWireMock with CleanMongoCollectionSupport {

  def commonStubs() = {}

  implicit override lazy val app = new GuiceApplicationBuilder()
    .configure(
      "mongodb.uri" -> mongoUri,
      "microservice.services.platform-analytics.port" -> wiremockPort
    )
    .build()

  private val invitationsRepo =
    new InvitationsRepositoryImpl(mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()

  }

  override implicit val patienceConfig = PatienceConfig(scaled(Span(10, Seconds)), scaled(Span(500, Millis)))

  private lazy val appConfig = app.injector.instanceOf[AppConfig]

  private lazy val analyticsConnector = app.injector.instanceOf[PlatformAnalyticsConnector]

  val intervalMillis = appConfig.invitationUpdateStatusInterval.seconds.toMillis
  val batchSize = appConfig.gaBatchSize

  private val actorSystem = app.injector.instanceOf[ActorSystem]

  private lazy val service = new PlatformAnalyticsService(
    invitationsRepo,
    analyticsConnector,
    appConfig,
    actorSystem
  )

  private def expireInvitations(i:List[Invitation], recently: Boolean): List[String] = {
    Future.sequence(i.map(
      x => invitationsRepo.update(
        x,Expired, if(recently) Instant.now().minusMillis(500).atZone(ZoneOffset.UTC).toLocalDateTime
        else now.minusDays(1))
        .map(_.invitationId.value))).futureValue
  }

  val now = LocalDateTime.now()

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
      now.minusDays(21),
      now.toLocalDate,
      Some("origin header"))
  }

  trait TestSetup {
    testClients.foreach(client => await(createInvitation(client)))
  }

  "PlatformAnalyticsService" should {

    "find invitations that may have expired only since the last update scheduler and send" in new TestSetup {

      val all = await(invitationsRepo.collection.find().toFuture()).toList

      expireInvitations(all.take(1), false)
      val _ = expireInvitations(all.drop(1), true)

      givenPlatformAnalyticsRequestSent(false)
      givenPlatformAnalyticsRequestSent(false)
      givenPlatformAnalyticsRequestSent(true)

      val result = await(service.reportExpiredInvitations())

      result shouldBe (())

      verifyAnalyticsRequestSent(1)

    }
  }

  "not find any invitations if they expired before the last update scheduler run" in new TestSetup {

    val all = await(invitationsRepo.collection.find().toFuture()).toList
    expireInvitations(all, false)

    val result = await(service.reportExpiredInvitations())

    result shouldBe (())

    verifyAnalyticsRequestWasNotSent()

  }
}
