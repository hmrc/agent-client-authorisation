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

package uk.gov.hmrc.agentclientauthorisation.model

import org.bson.types.ObjectId
import play.api.libs.json._
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, InvitationId, Service}
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import play.api.libs.functional.syntax._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationRecordFormat.{arnClientServiceStateKey, arnClientStateKey, createArnClientServiceStateKeys, createdKey, detailsForEmailKey, statusKey, toArnClientStateKey}

case class Invitation(
  _id: ObjectId = ObjectId.get(),
  invitationId: InvitationId,
  arn: Arn,
  clientType: Option[String],
  service: Service,
  clientId: ClientId,
  suppliedClientId: ClientId,
  expiryDate: LocalDate,
  detailsForEmail: Option[DetailsForEmail],
  isRelationshipEnded: Boolean = false,
  relationshipEndedBy: Option[String] = None,
  clientActionUrl: Option[String],
  origin: Option[String] = None,
  events: List[StatusChangeEvent],
  fromAcr: Boolean = false
) {

  def firstEvent(): StatusChangeEvent =
    events.head

  def mostRecentEvent(): StatusChangeEvent =
    events.last

  def status: InvitationStatus = mostRecentEvent().status

  def isPendingOn(date: LocalDate): Boolean = status == Pending && date.isBefore(expiryDate)

  val altItsa: Option[Boolean] = if (service == MtdIt) Some(clientId == suppliedClientId) else None
}

object Invitation {
  implicit val oidFormat: Format[ObjectId] = MongoFormats.Implicits.objectIdFormat
  val acrWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) =
      Json.obj(
        "invitationId"         -> invitation.invitationId.value,
        "arn"                  -> invitation.arn.value,
        "clientType"           -> invitation.clientType,
        "service"              -> invitation.service.id,
        "clientId"             -> invitation.clientId.value,
        "clientIdType"         -> invitation.clientId.typeId,
        "suppliedClientId"     -> invitation.suppliedClientId.value,
        "suppliedClientIdType" -> invitation.suppliedClientId.typeId,
        "events"               -> invitation.events,
        detailsForEmailKey     -> invitation.detailsForEmail,
        "relationshipEndedBy"  -> invitation.relationshipEndedBy,
        "expiryDate"           -> invitation.expiryDate
      )
  }
  val acrReads: Reads[Invitation] =
    (
      (__ \ "invitationId").read[String] and
        (__ \ "arn").read[String] and
        (__ \ "service").read[String] and
        (__ \ "clientId").read[String] and
        (__ \ "clientIdType").read[String] and
        (__ \ "suppliedClientId").read[String] and
        (__ \ "suppliedClientIdType").read[String] and
        (__ \ "clientName").read[String] and
        (__ \ "status").read[InvitationStatus] and
        (__ \ "relationshipEndedBy").readNullable[String] and
        (__ \ "clientType").readNullable[String] and
        (__ \ "expiryDate").read[LocalDate] and
        (__ \ "created").read[Instant] and
        (__ \ "lastUpdated").read[Instant]
    ) {
      (
        invitationId,
        arn,
        service,
        clientId,
        clientIdType,
        suppliedClientId,
        suppliedClientIdType,
        _,
        status,
        relationshipEndedBy,
        clientType,
        expiryDate,
        created,
        lastUpdated
      ) =>
        Invitation(
          invitationId = InvitationId(invitationId),
          arn = Arn(arn),
          clientType = clientType,
          service = Service.forId(service),
          clientId = ClientIdentifier(clientId, clientIdType),
          suppliedClientId = ClientIdentifier(suppliedClientId, suppliedClientIdType),
          expiryDate = expiryDate,
          detailsForEmail = None,
          isRelationshipEnded = relationshipEndedBy.isDefined,
          relationshipEndedBy = relationshipEndedBy,
          events = List(
            Some(StatusChangeEvent(created.atZone(ZoneOffset.UTC).toLocalDateTime, Pending)),
            if (created != lastUpdated) Some(StatusChangeEvent(lastUpdated.atZone(ZoneOffset.UTC).toLocalDateTime, status)) else None
          ).flatten,
          clientActionUrl = None,
          fromAcr = true,
          origin = None
        )
    }

  def createNew(
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: ClientId,
    suppliedClientId: ClientId,
    detailsForEmail: Option[DetailsForEmail],
    startDate: LocalDateTime,
    expiryDate: LocalDate,
    origin: Option[String]
  ): Invitation =
    Invitation(
      invitationId = InvitationId.create(arn.value, clientId.value, service.id)(service.invitationIdPrefix),
      arn = arn,
      clientType = clientType,
      service = service,
      clientId = clientId,
      suppliedClientId = suppliedClientId,
      expiryDate = expiryDate,
      detailsForEmail = detailsForEmail,
      clientActionUrl = None,
      origin = origin,
      events = List(StatusChangeEvent(startDate, Pending))
    )

  object external {
    implicit val dateTimeFormats: Format[LocalDateTime] = MongoLocalDateTimeFormat.localDateTimeFormat
    implicit val localDateFormats: Format[LocalDate] = MongoLocalDateTimeFormat.localDateFormat
    implicit val writes: Writes[Invitation] = new Writes[Invitation] {
      def writes(invitation: Invitation) =
        Json.obj(
          "clientType"           -> invitation.clientType,
          "service"              -> invitation.service.id,
          "clientIdType"         -> invitation.clientId.typeId,
          "clientId"             -> invitation.clientId.value,
          "arn"                  -> invitation.arn.value,
          "suppliedClientId"     -> invitation.suppliedClientId.value,
          "suppliedClientIdType" -> invitation.suppliedClientId.typeId,
          "created"              -> invitation.firstEvent().time.format(DateTimeFormatter.ISO_DATE_TIME),
          "lastUpdated"          -> invitation.mostRecentEvent().time.format(DateTimeFormatter.ISO_DATE_TIME),
          "expiryDate"           -> invitation.expiryDate.format(DateTimeFormatter.ISO_DATE),
          "status"               -> invitation.status,
          "invitationId"         -> invitation.invitationId.value,
          "detailsForEmail"      -> invitation.detailsForEmail,
          "isRelationshipEnded"  -> invitation.isRelationshipEnded,
          "relationshipEndedBy"  -> invitation.relationshipEndedBy,
          "clientActionUrl"      -> invitation.clientActionUrl,
          "origin"               -> invitation.origin
        )
    }
  }

  def toInvitationInfo(i: Invitation): InvitationInfo =
    InvitationInfo(
      i.invitationId,
      i.expiryDate,
      i.status,
      i.arn,
      i.service,
      i.isRelationshipEnded,
      i.relationshipEndedBy,
      i.events,
      (i.service == Service.MtdIt && i.suppliedClientId == i.clientId) || i.status == PartialAuth,
      i.fromAcr
    )
}
