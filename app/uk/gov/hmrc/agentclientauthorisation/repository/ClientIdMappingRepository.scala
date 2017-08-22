/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.libs.json.Json.toJsFieldJsValueWrapper
import reactivemongo.api.DB
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Ascending
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdMapping, Invitation}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdMapping.mongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientIdMappingRepository @Inject()(mongo: DB)
  extends ReactiveRepository[ClientIdMapping, BSONObjectID]("clientIdMapping", () => mongo, mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[ClientIdMapping] {


  override def indexes: Seq[Index] = Seq(
    Index(Seq("suppliedClientId" -> Ascending, "suppliedClientIdType" -> Ascending)),
    Index(Seq("canonicalClientId" -> Ascending, "canonicalClientIdType" -> Ascending,
      "suppliedClientId" -> Ascending, "suppliedClientIdType" -> Ascending), Some("uniqueIndex"), unique = true)
  )

  def create(canonicalClientId: String, canonicalClientIdType: String, suppliedClientId: String, suppliedClientIdType: String)
            (implicit ec: ExecutionContext): Future[ClientIdMapping] = withCurrentTime { now =>

    val request = ClientIdMapping(
      id = BSONObjectID.generate,
      canonicalClientId = canonicalClientId,
      canonicalClientIdType = canonicalClientIdType,
      suppliedClientId = suppliedClientId,
      suppliedClientIdType = suppliedClientIdType
    )

    insert(request).map(_ => request)
  }

  def find(suppliedClientId: String, suppliedClientIdType: String)(implicit ec: ExecutionContext): Future[List[ClientIdMapping]] = {
    val searchOptions = Seq(
      "suppliedClientId" -> Some(suppliedClientId),
      "suppliedClientIdType" -> Some(suppliedClientIdType)
    )
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: ClientIdMapping): Boolean =
    newRecordId != oldRecord.id

}
