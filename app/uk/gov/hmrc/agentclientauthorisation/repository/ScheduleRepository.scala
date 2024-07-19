/*
 * Copyright 2023 HM Revenue & Customs
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

import com.google.inject.ImplementedBy
import org.mongodb.scala.MongoWriteException
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.set
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions, Updates}
import play.api.Logging
import play.api.libs.json.Json.format
import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.model.MongoLocalDateTimeFormat
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}

import java.time.{Instant, LocalDateTime, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

sealed trait SchedulerType
case object InvitationExpired extends SchedulerType
case object RemovePersonalInfo extends SchedulerType

object SchedulerType {
  val mongoName = "schedulerType"
  implicit def writes: Writes[SchedulerType] =
    implicitly[Writes[String]].contramap[SchedulerType] {
      case InvitationExpired  => "InvitationExpired"
      case RemovePersonalInfo => "RemovePersonalInfo"
    }
  implicit def reads: Reads[SchedulerType] =
    implicitly[Reads[String]].collect[SchedulerType](JsonValidationError("Invalid SchedulerType format")) {
      case "InvitationExpired"  => InvitationExpired
      case "RemovePersonalInfo" => RemovePersonalInfo
    }
  implicit val schedulerTypeFormat: Format[SchedulerType] = Format(reads, writes)
}

case class ScheduleRecord(uid: String, runAt: LocalDateTime, schedulerType: SchedulerType)

object ScheduleRecord {

  implicit val localDateTimeFormat: Format[LocalDateTime] = MongoLocalDateTimeFormat.localDateTimeFormat

  implicit val formats: Format[ScheduleRecord] = format[ScheduleRecord]
}

@ImplementedBy(classOf[MongoScheduleRepository])
trait ScheduleRepository {
  def read(schedulerType: SchedulerType): Future[ScheduleRecord]
  def write(nextUid: String, nextRunAt: LocalDateTime, schedulerType: SchedulerType): Future[Unit]
}

@Singleton
class MongoScheduleRepository @Inject() (mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[ScheduleRecord](
      mongoComponent = mongoComponent,
      collectionName = "agent-schedule",
      domainFormat = ScheduleRecord.formats,
      indexes = Seq(
        IndexModel(ascending("uid", "runAt"), IndexOptions().unique(true)),
        IndexModel(ascending(SchedulerType.mongoName))
      ),
      extraCodecs = Codecs
        .playFormatCodecsBuilder(SchedulerType.schedulerTypeFormat)
        .forType[InvitationExpired.type]
        .forType[RemovePersonalInfo.type]
        .build
    ) with ScheduleRepository with Logging {

  override lazy val requiresTtlIndex: Boolean = false

  override def read(schedulerType: SchedulerType): Future[ScheduleRecord] =
    collection
      .find(equal(SchedulerType.mongoName, schedulerType))
      .headOption()
      .flatMap {
        case Some(record) => Future.successful(record)
        case None =>
          val record = ScheduleRecord(UUID.randomUUID().toString, Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime, schedulerType)
          collection
            .insertOne(record)
            .toFuture()
            .map(_ => record)
            .recoverWith { case ex: MongoWriteException =>
              logger.warn(s"Creating RecoveryRecord failed: ${ex.getMessage}")
              Future.failed(ex)
            }
      }

  override def write(newUid: String, newRunAt: LocalDateTime, schedulerType: SchedulerType): Future[Unit] =
    collection
      .findOneAndUpdate(
        Filters.equal(SchedulerType.mongoName, schedulerType),
        Updates.combine(set("uid", newUid), set("runAt", newRunAt), set(SchedulerType.mongoName, schedulerType))
      )
      .toFuture()
      .map(_ => ())
      .recover { case ex: MongoWriteException =>
        logger.error(s"Updating uid and runAt failed with error: ${ex.getMessage}")
      }
}
