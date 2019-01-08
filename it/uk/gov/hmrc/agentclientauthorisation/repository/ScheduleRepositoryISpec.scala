package uk.gov.hmrc.agentclientauthorisation.repository

import java.util.UUID

import org.joda.time.DateTime
import org.scalatestplus.play.OneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentclientauthorisation.support.MongoApp
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ScheduleRepositoryISpec
    extends UnitSpec
    with MongoApp
    with OneAppPerSuite {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "features.invitation-status-update-scheduler.enabled" -> false,
        "features.invitation-status-update-scheduler.interval" -> 30
      )
      .configure(mongoConfiguration)

  override implicit lazy val app: Application = appBuilder.build()

  private lazy val repo = app.injector.instanceOf[MongoScheduleRepository]

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  val uid = UUID.randomUUID()
  val newDate = DateTime.now()

  "RecoveryRepository" should {
    "read and write" in {
      val scheduleRecord =
        ScheduleRecord("foo", DateTime.parse("2017-10-31T23:22:50.971Z"))
      val newScheduleRecord =
        ScheduleRecord("foo", DateTime.parse("2019-10-31T23:22:50.971Z"))

      await(repo.insert(scheduleRecord))

      await(repo.read) shouldBe scheduleRecord

      await(repo.write("foo", DateTime.parse("2019-10-31T23:22:50.971Z")))

      await(repo.findAll()).head shouldBe newScheduleRecord
    }
  }

}
