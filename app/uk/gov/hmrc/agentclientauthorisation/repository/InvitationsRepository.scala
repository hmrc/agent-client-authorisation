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

import javax.inject._

import org.joda.time.DateTime
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{Json, Writes}
import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.BSONFormats
import uk.gov.hmrc.agentclientauthorisation.model.Invitation.mongoFormats
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{AtomicUpdate, ReactiveRepository}

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class InvitationsRepository @Inject()(mongo: DB)
  extends ReactiveRepository[Invitation, BSONObjectID]("invitations", () => mongo, mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AtomicUpdate[Invitation] {

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("invitationId" -> IndexType.Ascending), name = Some("invitationIdIndex"), unique = true),
    Index(Seq("arn" -> IndexType.Ascending)),
    Index(Seq("clientId" -> IndexType.Ascending)),
    Index(Seq("service" -> IndexType.Ascending))
  )

  def create(arn: Arn, service: Service, clientId: TaxIdentifier, postcode: Option[String], suppliedClientId: String, suppliedClientIdType: String)
            (implicit ec: ExecutionContext): Future[Invitation] = withCurrentTime { now =>

    val request = Invitation(
      id = BSONObjectID.generate,
      invitationId = InvitationId.create(arn.value, clientId.value, service.id)(service.invitationIdPrefix),
      arn = arn,
      service = service,
      clientId = clientId.value,
      postcode = postcode,
      suppliedClientId = suppliedClientId,
      suppliedClientIdType = suppliedClientIdType,
      events = List(StatusChangeEvent(now, Pending))
    )

    insert(request).map(_ => request)
  }

  def list(arn: Arn, service: Option[Service], clientId: Option[String], status: Option[InvitationStatus])(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val searchOptions = Seq("arn" -> Some(arn.value),
      "clientId" -> clientId,
      "service" -> service.map(_.id),
      "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'"))

      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  def list(service: Service, clientId: TaxIdentifier, status: Option[InvitationStatus])(implicit ec: ExecutionContext): Future[List[Invitation]] = {
    val searchOptions = Seq(
      "service" -> Some(service.id),
      "clientId" -> Some(clientId.value),
      statusSearchOption(status)
    )
      .filter(_._2.isDefined)
      .map(option => option._1 -> toJsFieldJsValueWrapper(option._2.get))

    find(searchOptions: _*)
  }

  private def statusSearchOption(status: Option[InvitationStatus]): (String, Option[String]) = {
    "$where" -> status.map(s => s"this.events[this.events.length - 1].status === '$s'")
  }

  def findRegimeID(clientId: String)(implicit ec: ExecutionContext): Future[List[Invitation]] =
    find()


  def update(id: BSONObjectID, status: InvitationStatus, updateDate: DateTime)(implicit ec: ExecutionContext): Future[Invitation] = {
    val update = atomicUpdate(BSONDocument("_id" -> id), BSONDocument("$push" -> BSONDocument("events" -> bsonJson(StatusChangeEvent(updateDate, status)))))
    update.map(_.map(_.updateType.savedValue).get)
  }

  private def bsonJson[T](entity: T)(implicit writes: Writes[T]) = BSONFormats.toBSON(Json.toJson(entity)).get

  override def isInsertion(newRecordId: BSONObjectID, oldRecord: Invitation): Boolean =
    newRecordId != oldRecord.id
}
