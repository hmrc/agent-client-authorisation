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

import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{Json, Writes}
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.json.BSONFormats
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository, Repository}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait InvitationsRepository extends Repository[Invitation, BSONObjectID] {

  def create(arn: Arn, regime: String, clientId: String, postcode: String): Future[Invitation]

  def update(id:BSONObjectID, status:InvitationStatus): Future[Invitation]

  def list(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]): Future[List[Invitation]]

  def list(regime: String, clientId: String, status: Option[InvitationStatus]): Future[List[Invitation]]

  def findRegimeID(clientId: String): Future[List[Invitation]]

  //def list(clientId : String, regime: Option[String], status: Option[InvitationStatus]): Future[List[Invitation]]

}

class InvitationsMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[Invitation, BSONObjectID]("invitations", mongo, Invitation.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with InvitationsRepository with AtomicUpdate[Invitation] {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("arn" -> IndexType.Ascending)),
    Index(Seq("clientId" -> IndexType.Ascending)),
    Index(Seq("regime" -> IndexType.Ascending))
  )

  override def create(arn: Arn, regime: String, clientId: String, postcode: String): Future[Invitation] = withCurrentTime { now =>

    val request = Invitation(
      id = BSONObjectID.generate,
      arn = arn,
      regime = regime,
      clientId = clientId,
      postcode = postcode,
      events = List(StatusChangeEvent(now, Pending))
    )

    insert(request).map(_ => request)
  }

  /*override def list(clientId : String, regime: Option[String], status: Option[InvitationStatus] ): Future[List[Invitation]] = {
    val searchOptions = Seq("clientId" -> clientId,
                            "regime" -> regime,
                            "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'"))

      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }*/

  override def list(arn: Arn, regime: Option[String], clientId: Option[String], status: Option[InvitationStatus]): Future[List[Invitation]] = {
    val searchOptions = Seq("arn" -> Some(arn.arn),
                            "clientId" -> clientId,
                            "regime" -> regime,
                            "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'"))

      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  override def list(regime: String, clientId: String, status: Option[InvitationStatus]): Future[List[Invitation]] = {
    val searchOptions = Seq(
      "regime" -> Some(regime),
      "clientId" -> Some(clientId),
      statusSearchOption(status)
    )
      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  private def statusSearchOption(status: Option[InvitationStatus]): (String, Option[String]) = {
    "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'")
  }

  override def findRegimeID(clientId: String): Future[List[Invitation]] =
    find()


  override def update(id: BSONObjectID, status: InvitationStatus): Future[Invitation] = withCurrentTime { now =>
    val update = atomicUpdate(BSONDocument("_id" -> id), BSONDocument("$push" -> BSONDocument("events" -> bsonJson(StatusChangeEvent(now, status)))))
    update.map(_.map(_.updateType.savedValue).get)
  }

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]) = BSONFormats.toBSON(Json.toJson(entity)).get

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: Invitation): Boolean =
    newRecordId != oldRecord.id
}
