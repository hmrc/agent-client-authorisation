package uk.gov.hmrc.agentclientauthorisation.repository

import java.util.UUID

import org.joda.time.DateTime
import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.support.{MetricsTestSupport, MongoApp, ResetMongoBeforeTest}
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ScheduleRepositoryISpec
    extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with MockitoSugar with MongoApp with MetricsTestSupport{

  override implicit lazy val mongoConnectorForTest: MongoConnector =
    MongoConnector(mongoUri, Some(MongoApp.failoverStrategyForTest))

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "invitation-status-update-scheduler.enabled" -> false
      )
      .configure(mongoConfiguration)

  lazy val app: Application = appBuilder.build()

  private lazy val repo = app.injector.instanceOf[MongoScheduleRepository]

  override def beforeEach() {
    super.beforeEach()
    givenCleanMetricRegistry()
    await(repo.ensureIndexes)
    ()
  }

  val uid = UUID.randomUUID()
  val newDate = DateTime.now()

  "RecoveryRepository" should {
    "read and write" in {
      val scheduleRecord = ScheduleRecord("foo", DateTime.parse("2017-10-31T23:22:50.971Z"),SchedulerType.InvitationExpired)
      val newScheduleRecord = ScheduleRecord("faa", DateTime.parse("2019-10-31T23:22:50.971Z"),SchedulerType.InvitationExpired)
      val otherRecord = ScheduleRecord("fuu", DateTime.parse("2017-10-31T23:22:50.971Z"),SchedulerType.RemovePersonalInfo)

      await(repo.insert(otherRecord))
      await(repo.insert(scheduleRecord))

      await(repo.read(SchedulerType.InvitationExpired)) shouldBe scheduleRecord

      await(repo.write("faa", DateTime.parse("2019-10-31T23:22:50.971Z"),SchedulerType.InvitationExpired))

      await(repo.findAll()).filter(_.schedulerType == SchedulerType.InvitationExpired ).head shouldBe newScheduleRecord
    }
  }

}
