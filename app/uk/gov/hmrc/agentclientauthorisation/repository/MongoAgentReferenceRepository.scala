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

import com.google.inject.ImplementedBy
import javax.inject._
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers
import uk.gov.hmrc.agentclientauthorisation.repository.AgentReferenceRecord.formats
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

case class AgentReferenceRecord(
  uid: String,
  arn: Arn,
  normalisedAgentNames: Seq[String]
)

object AgentReferenceRecord {
  implicit val formats: Format[AgentReferenceRecord] = Json.format[AgentReferenceRecord]
}

@ImplementedBy(classOf[MongoAgentReferenceRepository])
trait AgentReferenceRepository {
  def create(multiInvitationRecord: AgentReferenceRecord)(implicit ec: ExecutionContext): Future[Int]
  def findBy(uid: String)(implicit ec: ExecutionContext): Future[Option[AgentReferenceRecord]]
  def findByArn(arn: Arn)(implicit ec: ExecutionContext): Future[Option[AgentReferenceRecord]]
  def updateAgentName(uid: String, newAgentName: String)(implicit ex: ExecutionContext): Future[Unit]
}

@Singleton
class MongoAgentReferenceRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[AgentReferenceRecord, BSONObjectID](
      "agent-reference",
      mongo.mongoConnector.db,
      formats,
      ReactiveMongoFormats.objectIdFormats) with AgentReferenceRepository
    with StrictlyEnsureIndexes[AgentReferenceRecord, BSONObjectID] {

  override def indexes: Seq[Index] =
    Seq(
      Index(Seq("uid" -> IndexType.Ascending), unique = true),
      Index(Seq("arn" -> IndexType.Ascending), unique = true)
    )

  def create(multiInvitationRecord: AgentReferenceRecord)(implicit ec: ExecutionContext): Future[Int] =
    insert(multiInvitationRecord).map { result =>
      result.writeErrors.foreach(error =>
        Logger(getClass).warn(s"Creating MultiInvitationRecord failed: ${error.errmsg}"))
      result.n
    }

  def findBy(uid: String)(implicit ex: ExecutionContext): Future[Option[AgentReferenceRecord]] =
    find(
      "uid" -> uid
    ).map(_.headOption)

  def findByArn(arn: Arn)(implicit ex: ExecutionContext): Future[Option[AgentReferenceRecord]] =
    find(
      "arn" -> arn.value
    ).map(_.headOption)

  import ImplicitBSONHandlers._

  def updateAgentName(uid: String, newAgentName: String)(implicit ex: ExecutionContext): Future[Unit] =
    collection
      .update(Json.obj("uid" -> uid), Json.obj("$addToSet" -> Json.obj("normalisedAgentNames" -> newAgentName)))
      .map(_ => ())

}
