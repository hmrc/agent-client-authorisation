package uk.gov.hmrc.agentclientauthorisation.repository

import java.util.UUID

import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, ResetMongoBeforeTest}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ScheduleRepositoryISpec
    extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with MockitoSugar with MongoApp {

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(
        "invitation-status-update-scheduler.enabled" -> false,
        "mongodb-migration.enabled"                  -> false
      )
      .configure(mongoConfiguration)

  lazy val app: Application = appBuilder.build()

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
