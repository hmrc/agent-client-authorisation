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
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Source
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.Updates.addToSet
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.RelationshipsConnector
import uk.gov.hmrc.agentclientauthorisation.repository.AgentReferenceRecord.formats
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject._
import scala.collection.Seq
import scala.concurrent.duration.DurationInt
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
  def create(agentReferenceRecord: AgentReferenceRecord): Future[Option[String]]

  def findBy(uid: String): Future[Option[AgentReferenceRecord]]

  def findByArn(arn: Arn): Future[Option[AgentReferenceRecord]]

  def updateAgentName(uid: String, newAgentName: String): Future[Unit]

  def removeAgentReferencesForGiven(arn: Arn): Future[Int]

  def countRemaining(): Future[Long]

  def migrateToAcr(rate: Int = 10)(implicit hc: HeaderCarrier): Future[Done]

  def testOnlyDropAgentReferenceCollection(): Future[Unit]
}

@Singleton
class MongoAgentReferenceRepository @Inject() (
  mongo: MongoComponent,
  acrConnector: RelationshipsConnector,
  lockClient: LockClient
)(implicit ec: ExecutionContext, mat: Materializer, appConfig: AppConfig)
    extends PlayMongoRepository[AgentReferenceRecord](
      mongoComponent = mongo,
      collectionName = "agent-reference",
      domainFormat = formats,
      indexes = List(
        IndexModel(ascending("uid"), IndexOptions().unique(true)),
        IndexModel(ascending("arn"), IndexOptions().unique(true))
      )
    ) with AgentReferenceRepository with Logging {

  override lazy val requiresTtlIndex: Boolean = false

  override def create(agentReferenceRecord: AgentReferenceRecord): Future[Option[String]] =
    collection
      .insertOne(agentReferenceRecord)
      .headOption()
      .map(_.map(_.getInsertedId.asObjectId().getValue.toString))

  override def findBy(uid: String): Future[Option[AgentReferenceRecord]] =
    collection
      .find(equal("uid", uid))
      .headOption()

  override def findByArn(arn: Arn): Future[Option[AgentReferenceRecord]] =
    collection
      .find(equal("arn", arn.value))
      .headOption()

  override def updateAgentName(uid: String, newAgentName: String): Future[Unit] =
    collection
      .updateOne(equal("uid", uid), addToSet("normalisedAgentNames", newAgentName))
      .toFuture()
      .map(uor => logger.info(s"Matched: ${uor.getMatchedCount}; Updated: ${uor.getModifiedCount}"))

  override def removeAgentReferencesForGiven(arn: Arn): Future[Int] =
    collection
      .deleteOne(equal("arn", arn.value))
      .toFuture()
      .map(r => r.getDeletedCount.toInt)

  override def countRemaining(): Future[Long] =
    collection.countDocuments().toFuture()

  override def migrateToAcr(rate: Int = 10)(implicit hc: HeaderCarrier): Future[Done] = {
    val observable = collection.find()
    countRemaining().map { count =>
      logger.info(s"[AgentReferenceRepository] migrating started, $count records to migrate")
    }
    Source
      .fromPublisher(observable)
      .throttle(rate, 1.second)
      .runForeach { record =>
        for {
          migrateResult <- acrConnector.migrateAgentReferenceRecord(record)
        } yield migrateResult match {
          case Some("OK") => removeAgentReferencesForGiven(record.arn).map(_ => ())
          case _ =>
            logger.warn(s"[AgentReferenceRepository] failed to migrate record with uid ${record.uid}")
        }
      }
      .recover { case _: Throwable =>
        logger.warn("[AgentReferenceRepository] failed to read record before migrating, aborting process")
        Done
      }
      .map { _ =>
        countRemaining().map { count =>
          logger.warn(s"[AgentReferenceRepository] migration completed, $count records left to migrate")
        }
        Done
      }
  }

  def testOnlyDropAgentReferenceCollection(): Future[Unit] = collection.drop().toFuture().map(_ => ())

  if (appConfig.acrAgentReferenceMigrate) {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    logger.warn("[AgentReferenceRepository] migration is starting...")
    lockClient.migrateWithLock(body = migrateToAcr())
  } else {
    logger.warn(s"[AgentReferenceRepository] migration is disabled")
  }

}
