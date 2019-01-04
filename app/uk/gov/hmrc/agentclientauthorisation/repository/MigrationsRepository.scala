/*
 * Copyright 2019 HM Revenue & Customs
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

import javax.inject._
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

case class Migration(id: String, started: DateTime, finished: Option[DateTime] = None)

object Migration {
  implicit val formats: OFormat[Migration] = Json.format[Migration]
}

@Singleton
class MigrationsRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Migration, BSONObjectID](
      "migrations",
      mongo.mongoConnector.db,
      Migration.formats,
      ReactiveMongoFormats.objectIdFormats) with AtomicUpdate[Migration]
    with StrictlyEnsureIndexes[Migration, BSONObjectID] {

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("id" -> IndexType.Ascending), name = Some("invitationIdIndex"), unique = true)
    )

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: Migration): Boolean = true

  def tryLock(id: String)(implicit ec: ExecutionContext): Future[Unit] =
    insert(Migration(id, DateTime.now(DateTimeZone.UTC))).map(_ => ())

  def markDone(id: String)(implicit ec: ExecutionContext): Future[Unit] =
    find("id" -> id).map(_.headOption.map(migration =>
      atomicUpdate(BSONDocument("id" -> id), bsonJson(migration.copy(finished = Some(DateTime.now(DateTimeZone.UTC)))))))

  import ImplicitBSONHandlers._

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]): BSONDocument =
    BSONDocumentFormat.reads(writes.writes(entity)).get

}
