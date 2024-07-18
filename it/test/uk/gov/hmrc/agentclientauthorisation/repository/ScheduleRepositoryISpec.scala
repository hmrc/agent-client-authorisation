/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation.repository

import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.LocalDateTime
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class ScheduleRepositoryISpec extends UnitSpec with DefaultPlayMongoRepositorySupport[ScheduleRecord] {

  override val repository = new MongoScheduleRepository(mongoComponent)

  val uid = UUID.randomUUID()
  val newDate = LocalDateTime.now()

  "RecoveryRepository" should {
    "read and write" in {
      val scheduleRecord = ScheduleRecord("foo", LocalDateTime.parse("2017-10-31T23:22:50.971"), InvitationExpired)
      val newScheduleRecord = ScheduleRecord("faa", LocalDateTime.parse("2019-10-31T23:22:50.971"), InvitationExpired)
      val otherRecord = ScheduleRecord("fuu", LocalDateTime.parse("2017-10-31T23:22:50.971"), RemovePersonalInfo)

      await(repository.collection.insertOne(otherRecord).toFuture())
      await(repository.collection.insertOne(scheduleRecord).toFuture())

      await(repository.read(InvitationExpired)) shouldBe scheduleRecord

      await(repository.write("faa", LocalDateTime.parse("2019-10-31T23:22:50.971"), InvitationExpired))

      await(repository.collection.find().toFuture()).filter(_.schedulerType == InvitationExpired).head shouldBe newScheduleRecord
    }
  }

}
