/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.libs.json.{Json, Writes}
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.json.BSONFormats
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationRequest, AuthorisationStatus, Pending, StatusChangeEvent}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository, Repository}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AuthorisationRequestRepository extends Repository[AgentClientAuthorisationRequest, BSONObjectID] {

  def create(agentCode: AgentCode, regime: String, clientRegimeId: String, agentUserDetailsLink: String): Future[AgentClientAuthorisationRequest]

  def update(id:BSONObjectID, status:AuthorisationStatus): Future[AgentClientAuthorisationRequest]

  def list(agentCode: AgentCode): Future[List[AgentClientAuthorisationRequest]]

  def list(regime: String, clientRegimeId: String): Future[List[AgentClientAuthorisationRequest]]

}

class AuthorisationRequestMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AgentClientAuthorisationRequest, BSONObjectID]("authorisationRequests", mongo, AgentClientAuthorisationRequest.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AuthorisationRequestRepository with AtomicUpdate[AgentClientAuthorisationRequest] {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("agentCode" -> IndexType.Ascending)),
    Index(Seq("clientRegimeId" -> IndexType.Ascending)),
    Index(Seq("requestDate" -> IndexType.Ascending))
  )

  override def create(agentCode: AgentCode, regime: String, clientRegimeId: String, agentUserDetailsLink: String): Future[AgentClientAuthorisationRequest] = withCurrentTime { now =>

    val request = AgentClientAuthorisationRequest(
      id = BSONObjectID.generate,
      agentCode = agentCode,
      regime = regime,
      clientRegimeId = clientRegimeId,
      agentUserDetailsLink = agentUserDetailsLink,
      events = List(StatusChangeEvent(now, Pending))
    )

    insert(request).map(_ => request)
  }


  override def list(agentCode: AgentCode): Future[List[AgentClientAuthorisationRequest]] =
    find("agentCode" -> agentCode)

  override def list(regime: String, clientRegimeId: String): Future[List[AgentClientAuthorisationRequest]] =
    find("regime" -> regime, "clientRegimeId" -> clientRegimeId)

  override def update(id: BSONObjectID, status: AuthorisationStatus): Future[AgentClientAuthorisationRequest] = withCurrentTime { now =>
    val update = atomicUpdate(BSONDocument("_id" -> id), BSONDocument("$push" -> BSONDocument("events" -> bsonJson(StatusChangeEvent(now, status)))))
    update.map(_.map(_.updateType.savedValue).get)
  }

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]) = BSONFormats.toBSON(Json.toJson(entity)).get

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: AgentClientAuthorisationRequest): Boolean =
    newRecordId != oldRecord.id
}






//"""{
//  [
//
//    { _id: "", agentCode: "", clientRegimeId: "", events: [{ time: 123456789, name: "Requested" }, { time: 123456789, name: "Requested" }]},
//    { _id: "", agentCode: "", clientRegimeId: "", events: [{ time: 123456789, status: "pending" }, { time: 123456789, status: "accepted" }]},
//
//  ]
//}""""
