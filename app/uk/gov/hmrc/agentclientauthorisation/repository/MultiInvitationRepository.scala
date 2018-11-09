/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.DateTime
import play.api.Logger
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.repository.MultiInvitationRecord.formats
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

case class MultiInvitationRecord(
  uid: String, //unique in index
  arn: Arn,
  invitationIds: Seq[InvitationId],
  clientType: String,
  createdDate: DateTime,
  expiryDate: DateTime //10 days from creation date
)

object MultiInvitationRecord {
  implicit val formats: Format[MultiInvitationRecord] = Json.format[MultiInvitationRecord]
}

case class ReceivedMultiInvitation(arn: Arn, agentName: String, clientType: String, invitationIds: Seq[InvitationId])

object ReceivedMultiInvitation {
  implicit val format = Json.format[ReceivedMultiInvitation]

  def normalizeAgentName(agentName: String) =
    agentName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9-]", "")
}

trait MultiInvitationRecordRepository {
  def create(multiInvitationRecord: MultiInvitationRecord)(implicit ec: ExecutionContext): Future[Int]
  def findBy(uid: String)(implicit ec: ExecutionContext): Future[Option[MultiInvitationRecord]]
  //update and remove??
}

@Singleton
class MultiInvitationRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[MultiInvitationRecord, BSONObjectID](
      "multi-invitation-record",
      mongo.mongoConnector.db,
      formats,
      ReactiveMongoFormats.objectIdFormats) with MultiInvitationRecordRepository
    with StrictlyEnsureIndexes[MultiInvitationRecord, BSONObjectID] {

  override def indexes: Seq[Index] =
    Seq(
      Index(Seq("uuid" -> IndexType.Ascending), unique = true)
    )

  def create(multiInvitationRecord: MultiInvitationRecord)(implicit ec: ExecutionContext): Future[Int] =
    insert(multiInvitationRecord).map { result =>
      result.writeErrors.foreach(error =>
        Logger(getClass).warn(s"Creating MultiInvitationRecord failed: ${error.errmsg}"))
      result.n
    }

  def findBy(uid: String)(implicit ex: ExecutionContext): Future[Option[MultiInvitationRecord]] =
    find(
      "uid" -> uid
    ).map(_.headOption)

}
