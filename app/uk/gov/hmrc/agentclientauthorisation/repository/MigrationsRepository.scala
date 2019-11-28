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

import javax.inject.{Inject, Singleton}
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.functional.syntax._
import play.api.libs.json.JodaWrites._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

object Migration {

  val dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ"

  val jodaDateReads = Reads[DateTime](js =>
    js.validate[String].map[DateTime](dtString => DateTime.parse(dtString, DateTimeFormat.forPattern(dateFormat))))

  val jodaDateWrites: Writes[DateTime] = new Writes[DateTime] {
    def writes(d: DateTime): JsValue = JsString(d.toString())
  }

  val migrationReads: Reads[Migration] = (
    (JsPath \ "id").read[String] and
      (JsPath \ "started").read[DateTime](jodaDateReads) and
      (JsPath \ "finished").readNullable[DateTime](jodaDateReads)
  )(Migration.apply _)

  val migrationWrites: Writes[Migration] = (
    (JsPath \ "id").write[String] and
      (JsPath \ "started").write[DateTime](jodaDateWrites) and
      (JsPath \ "finished").writeNullable[DateTime](jodaDateWrites)
  )(unlift(Migration.unapply))

  implicit val migrationFormats: Format[Migration] = Format(migrationReads, migrationWrites)
}

case class Migration(id: String, started: DateTime, finished: Option[DateTime] = None)
@Singleton
class MigrationsRepository @Inject()(implicit mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Migration, BSONObjectID](
      "migrations",
      mongo.mongoConnector.db,
      implicitly[Format[Migration]],
      ReactiveMongoFormats.objectIdFormats) with StrictlyEnsureIndexes[Migration, BSONObjectID] {

  override def indexes: Seq[Index] =
    Seq(
      Index(key = Seq("id" -> IndexType.Ascending), name = Some("invitationIdIndex"), unique = true)
    )

  def tryLock(id: String)(implicit ec: ExecutionContext): Future[Unit] =
    insert(Migration(id, DateTime.now(DateTimeZone.UTC))).map(_ => ())

  def markDone(id: String)(implicit ec: ExecutionContext) = {
    val selector = Json.obj("id" -> id)
    val update = Json.obj("$set" -> Json.obj("finished" -> Some(DateTime.now(DateTimeZone.UTC))))
    collection
      .update(ordered = false)
      .one(selector, update)
  }
}
