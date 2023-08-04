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
import org.mongodb.scala.model.{Filters, IndexModel, IndexOptions}
import org.mongodb.scala.model.Indexes.ascending
import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.model.AgentSuspensionContactDetailsCaptured
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[AgentSuspensionContactDetailsRepositoryImpl])
trait AgentSuspensionContactDetailsCapturedRepository {
  def get(arn: Arn): Future[Option[AgentSuspensionContactDetailsCaptured]]
  def create(arn: Arn): Future[Unit]
}

class AgentSuspensionContactDetailsRepositoryImpl @Inject()(mongo: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[AgentSuspensionContactDetailsCaptured](
      mongoComponent = mongo,
      collectionName = "agent-suspension-details-already-captured",
      domainFormat = AgentSuspensionContactDetailsCaptured.format,
      indexes = Seq(
        IndexModel(ascending("arn"), IndexOptions().unique(true))
      )
    ) with AgentSuspensionContactDetailsCapturedRepository with Logging {

  def get(arn: Arn): Future[Option[AgentSuspensionContactDetailsCaptured]] =
    collection
      .find(Filters.equal("arn", arn.value))
      .headOption()

  def create(arn: Arn): Future[Unit] =
    collection
      .insertOne(AgentSuspensionContactDetailsCaptured(arn, java.time.Instant.now()))
      .toFuture()
      .map(insertResult =>
        if (insertResult.wasAcknowledged()) ()
        else throw new RuntimeException("Could not insert AgentSuspensionContactDetailsCaptured record"))

}
