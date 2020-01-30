/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json.format
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.WriteConcern
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

case class ScheduleRecord(uid: String, runAt: DateTime)

object ScheduleRecord extends ReactiveMongoFormats {
  implicit val formats: Format[ScheduleRecord] = format[ScheduleRecord]
}

@ImplementedBy(classOf[MongoScheduleRepository])
trait ScheduleRepository {
  def read(implicit ec: ExecutionContext): Future[ScheduleRecord]
  def write(nextUid: String, nextRunAt: DateTime)(implicit ec: ExecutionContext): Future[Unit]
}

@Singleton
class MongoScheduleRepository @Inject()(mongoComponent: ReactiveMongoComponent)
    extends ReactiveRepository[ScheduleRecord, BSONObjectID](
      "agent-schedule",
      mongoComponent.mongoConnector.db,
      ScheduleRecord.formats,
      ReactiveMongoFormats.objectIdFormats) with ScheduleRepository
    with StrictlyEnsureIndexes[ScheduleRecord, BSONObjectID] {

  import ImplicitBSONHandlers._

  override def indexes =
    Seq(Index(Seq("uid" -> Ascending, "runAt" -> Ascending), unique = true))

  def read(implicit ec: ExecutionContext): Future[ScheduleRecord] =
    findAll().flatMap(_.headOption match {
      case Some(record) => Future.successful(record)
      case None =>
        val record = ScheduleRecord(UUID.randomUUID().toString, DateTime.now())
        insert(record).map(_ => record).recoverWith {
          case NonFatal(error) =>
            Logger(getClass).warn(s"Creating RecoveryRecord failed: ${error.getMessage}")
            Future.failed(error)
        }
    })

  def write(newUid: String, newRunAt: DateTime)(implicit ec: ExecutionContext): Future[Unit] =
    collection
      .findAndUpdate(
        selector = BSONDocument(),
        update = BSONDocument(
          "$set" -> BSONDocument("uid" -> newUid, "runAt" -> ReactiveMongoFormats.dateTimeWrite.writes(newRunAt))),
        fetchNewObject = false,
        upsert = false,
        sort = None,
        fields = None,
        bypassDocumentValidation = false,
        writeConcern = WriteConcern.Default,
        maxTime = None,
        collation = None,
        arrayFilters = Nil
      )
      .map(_.lastError.foreach(error => Logger.warn(s"Updating uid and runAt failed with error: $error")))
}
