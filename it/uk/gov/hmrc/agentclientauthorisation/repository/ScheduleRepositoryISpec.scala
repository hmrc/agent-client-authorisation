package uk.gov.hmrc.agentclientauthorisation.repository

import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class ScheduleRepositoryISpec
    extends UnitSpec
      with DefaultPlayMongoRepositorySupport[ScheduleRecord]{

  override def repository = new MongoScheduleRepository(mongoComponent)

  val uid = UUID.randomUUID()
  val newDate = LocalDateTime.now()

  "RecoveryRepository" should {
    "read and write" in {
      val scheduleRecord = ScheduleRecord("foo", LocalDateTime.parse("2017-10-31T23:22:50.971"),InvitationExpired)
      val newScheduleRecord = ScheduleRecord("faa", LocalDateTime.parse("2019-10-31T23:22:50.971"),InvitationExpired)
      val otherRecord = ScheduleRecord("fuu", LocalDateTime.parse("2017-10-31T23:22:50.971"),RemovePersonalInfo)

      await(repository.collection.insertOne(otherRecord).toFuture())
      await(repository.collection.insertOne(scheduleRecord).toFuture())

      await(repository.read(InvitationExpired)) shouldBe scheduleRecord

      await(repository.write("faa", LocalDateTime.parse("2019-10-31T23:22:50.971"),InvitationExpired))

      await(repository.collection.find().toFuture()).filter(_.schedulerType == InvitationExpired ).head shouldBe newScheduleRecord
    }
  }

}
