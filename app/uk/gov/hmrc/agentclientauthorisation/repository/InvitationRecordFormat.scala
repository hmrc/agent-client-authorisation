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
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JodaReads._
import play.api.libs.json.JodaWrites._
import play.api.libs.json.{Format, JsValue, Json, Writes}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Reads}

object InvitationRecordFormat {

  def read(
    id: BSONObjectID,
    invitationId: InvitationId,
    arn: Arn,
    clientType: Option[String],
    service: Service,
    clientId: String,
    clientIdTypeOp: Option[String],
    suppliedClientId: String,
    suppliedClientIdType: String,
    expiryDateOp: Option[LocalDate],
    detailsForEmail: Option[DetailsForEmail],
    clientActionUrl: Option[String],
    events: List[StatusChangeEvent]): Invitation = {

    val expiryDate = expiryDateOp.getOrElse(events.head.time.plusDays(14).toLocalDate)

    val clientIdType = clientIdTypeOp.getOrElse {
      if (Nino.isValid(clientId)) NinoType.id else MtdItIdType.id
    }

    Invitation(
      id,
      invitationId,
      arn,
      clientType,
      service,
      ClientIdentifier(clientId, clientIdType),
      ClientIdentifier(suppliedClientId, suppliedClientIdType),
      expiryDate,
      detailsForEmail,
      None,
      events
    )
  }

  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats

  val reads: Reads[Invitation] = ((JsPath \ "id").read[BSONObjectID] and
    (JsPath \ "invitationId").read[InvitationId] and
    (JsPath \ "arn").read[Arn] and
    (JsPath \ "clientType").readNullable[String] and
    (JsPath \ "service").read[Service] and
    (JsPath \ "clientId").read[String] and
    (JsPath \ "clientIdType").readNullable[String] and
    (JsPath \ "suppliedClientId").read[String] and
    (JsPath \ "suppliedClientIdType").read[String] and
    (JsPath \ "expiryDate").readNullable[LocalDate] and
    (JsPath \ "detailsForEmail").readNullable[DetailsForEmail] and
    (JsPath \ "clientActionUrl").readNullable[String] and
    (JsPath \ "events").read[List[StatusChangeEvent]])(read _)

  val arnClientStateKey = "_arnClientStateKey"
  val arnClientServiceStateKey = "_arnClientServiceStateKey"
  val statusKey = "_status"
  val createdKey = "_created"

  val writes = new Writes[Invitation] {
    def writes(invitation: Invitation) =
      Json.obj(
        "id"                   -> invitation.id,
        "invitationId"         -> invitation.invitationId,
        "arn"                  -> invitation.arn.value,
        "clientType"           -> invitation.clientType,
        "service"              -> invitation.service.id,
        "clientId"             -> invitation.clientId.value,
        "clientIdType"         -> invitation.clientId.typeId,
        "suppliedClientId"     -> invitation.suppliedClientId.value,
        "suppliedClientIdType" -> invitation.suppliedClientId.typeId,
        "events"               -> invitation.events,
        "detailsForEmail"      -> invitation.detailsForEmail,
        "clientActionUrl"      -> invitation.clientActionUrl,
        "expiryDate"           -> invitation.expiryDate,
        createdKey             -> invitation.firstEvent().time.getMillis,
        statusKey              -> invitation.mostRecentEvent().status.toString,
        arnClientStateKey -> Seq(
          toArnClientStateKey(
            invitation.arn.value,
            invitation.clientId.enrolmentId,
            invitation.clientId.value,
            invitation.mostRecentEvent().status.toString),
          toArnClientStateKey(
            invitation.arn.value,
            invitation.suppliedClientId.enrolmentId,
            invitation.suppliedClientId.value,
            invitation.mostRecentEvent().status.toString)
        ),
        arnClientServiceStateKey -> createArnClientServiceStateKeys(invitation)
      )
  }

  def toArnClientStateKey(arn: String, clientIdType: String, clientIdValue: String, status: String): String =
    s"${arn.toLowerCase}~${clientIdType.toLowerCase}~${clientIdValue.toLowerCase.replaceAll(" ", "")}~${status.toLowerCase}"

  def createArnClientServiceStateKeys(inv: Invitation): Seq[String] =
    variants(inv.arn, inv.clientId.value, inv.service, inv.mostRecentEvent().status)(toArnClientServiceStateKey) ++
      (if (inv.clientId != inv.suppliedClientId)
         variants(inv.arn, inv.suppliedClientId.value, inv.service, inv.mostRecentEvent().status)(
           toArnClientServiceStateKey)
       else Seq.empty)

  def toArnClientServiceStateKey(
    arn: Option[Arn],
    clientId: Option[String],
    service: Option[Service],
    status: Option[InvitationStatus]): String =
    Seq(
      arn.map(_.value.toLowerCase),
      clientId.map(_.toLowerCase.replaceAll(" ", "")),
      service.map(_.id.toLowerCase),
      status.map(_.toString.toLowerCase)
    ).collect { case Some(x) => x }
      .mkString("~")

  val mongoFormat = ReactiveMongoFormats.mongoEntity(Format(reads, writes))

  private def variants[A, B, C, D, T](a: A, b: B, c: C, d: D)(
    fx: (Option[A], Option[B], Option[C], Option[D]) => T): Seq[T] =
    Seq(
      fx(Some(a), Some(b), Some(c), Some(d)),
      fx(Some(a), Some(b), None, Some(d)),
      fx(Some(a), None, Some(c), Some(d)),
      fx(Some(a), None, None, Some(d)),
      fx(None, Some(b), Some(c), Some(d)),
      fx(None, Some(b), None, Some(d)),
      fx(None, None, Some(c), Some(d)),
      fx(None, None, None, Some(d)),
      fx(Some(a), Some(b), Some(c), None),
      fx(Some(a), Some(b), None, None),
      fx(Some(a), None, Some(c), None),
      fx(Some(a), None, None, None),
      fx(None, Some(b), Some(c), None),
      fx(None, Some(b), None, None),
      fx(None, None, Some(c), None)
    )

}
