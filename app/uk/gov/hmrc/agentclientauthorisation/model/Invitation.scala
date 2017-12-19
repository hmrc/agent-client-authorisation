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

package uk.gov.hmrc.agentclientauthorisation.model

import org.joda.time.DateTime
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.domain.{Nino, SimpleObjectReads, SimpleObjectWrites, TaxIdentifier}
import uk.gov.hmrc.http.controllers.RestFormats
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

sealed trait InvitationStatus {

  def toEither: Either[String, InvitationStatus] = this match {
    case Unknown(status) => Left(status)
    case status => Right(status)
  }

  def leftMap[X](f: String => X) =
    toEither.left.map(f)
}

case object Pending extends InvitationStatus

case object Expired extends InvitationStatus

case object Rejected extends InvitationStatus

case object Accepted extends InvitationStatus

case object Cancelled extends InvitationStatus

case class Unknown(attempted: String) extends InvitationStatus

object InvitationStatus {
  def unapply(status: InvitationStatus): Option[String] = status match {
    case Pending => Some("Pending")
    case Rejected => Some("Rejected")
    case Accepted => Some("Accepted")
    case Cancelled => Some("Cancelled")
    case Expired => Some("Expired")
    case _ => None
  }

  def apply(status: String): InvitationStatus = status.toLowerCase match {
    case "pending" => Pending
    case "rejected" => Rejected
    case "accepted" => Accepted
    case "cancelled" => Cancelled
    case "expired" => Expired
    case _ => Unknown(status)
  }

  implicit val invitationStatusFormat = new Format[InvitationStatus] {
    override def reads(json: JsValue): JsResult[InvitationStatus] = apply(json.as[String]) match {
      case Unknown(value) => JsError(s"Status of [$value] is not a valid InvitationStatus")
      case value => JsSuccess(value)
    }

    override def writes(o: InvitationStatus): JsValue = unapply(o).map(JsString).getOrElse(throw new IllegalArgumentException)
  }
}

case class StatusChangeEvent(time: DateTime, status: InvitationStatus)

case class ClientIdMapping(id: BSONObjectID,
                           canonicalClientId: String,
                           canonicalClientIdType: String,
                           suppliedClientId: String,
                           suppliedClientIdType: String)

object ClientIdMapping {
  implicit val dateWrites = RestFormats.dateTimeWrite
  implicit val dateReads = RestFormats.dateTimeRead
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "canonicalClientId" -> invitation.clientIdType,
      "canonicalClientIdType" -> invitation.clientId,
      "suppliedClientId" -> invitation.suppliedClientId,
      "suppliedClientIdType" -> invitation.suppliedClientIdType,
      "created" -> invitation.firstEvent().time,
      "lastUpdated" -> invitation.mostRecentEvent().time
    )

  }

  val mongoFormats = ReactiveMongoFormats.mongoEntity(Json.format[ClientIdMapping])
}

case class Invitation(
                       id: BSONObjectID,
                       invitationId: InvitationId,
                       arn: Arn,
                       service: Service,
                       clientId: String,
                       postcode: Option[String],
                       suppliedClientId: String,
                       suppliedClientIdType: String,
                       events: List[StatusChangeEvent]) {

  val clientIdType = NinoType.id // only supported type as of now

  def firstEvent(): StatusChangeEvent = {
    events.head
  }

  def mostRecentEvent(): StatusChangeEvent = {
    events.last
  }

  def status = mostRecentEvent().status
}

/** Information provided by the agent to offer representation to HMRC */
case class AgentInvitation(
                            service: String,
                            clientIdType: String,
                            clientId: String,
                            clientPostcode: Option[String])

object StatusChangeEvent {
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
}


sealed abstract class ClientIdType[T<:TaxIdentifier](val clazz: Class[T], val id: String, val enrolmentId: String)
case object NinoType extends ClientIdType(classOf[Nino], "ni", "NINO")
case object MtdItIdType extends ClientIdType(classOf[MtdItId], "MTDITID", "MTDITID")
private object ClientIdType {
  val supportedTypes = Seq(NinoType, MtdItIdType)
  def forId(id: String) = supportedTypes.find(_.id == id).getOrElse(throw new IllegalArgumentException("Invalid id:" + id))
  
}

case class ClientId[T<:TaxIdentifier](underlying: T) {

  private val clientIdType = ClientIdType.supportedTypes.find(_.clazz == underlying.getClass)
    .getOrElse(throw new Exception("Invalid type for clientId " + underlying.getClass.getCanonicalName))

  val value: String = underlying.value
  val `type`: Class[_<:TaxIdentifier] = clientIdType.clazz
  val id: String = clientIdType.id
  val enrolmentId: String = clientIdType.enrolmentId
}

object ClientId {
  implicit def taxIdAsClientId[T<:TaxIdentifier](taxId: T): ClientId[T] = ClientId(taxId)
}

sealed abstract class Service(val id: String, val invitationIdPrefix: Char, val enrolmentKey: String) {

  override def equals(that: Any): Boolean =
    that match {
      case that: Service => this.id.equals(that.id)
      case _ => false
  }
}

object Service {
  case object MtdIt extends Service("HMRC-MTD-IT", 'A', "HMRC-MTD-IT")
  case object PersonalIncomeRecord extends Service("PERSONAL-INCOME-RECORD", 'B', "HMRC-NI")

  val values = Seq(MtdIt, PersonalIncomeRecord)
  def findById(id: String): Option[Service] = values.find(_.id == id)
  def forId(id: String): Service = findById(id).getOrElse(throw new Exception("Not a valid service"))

  def apply(id: String) = forId(id)
  def unapply(service: Service): Option[String] = Some(service.id)

  val reads = new SimpleObjectReads[Service]("id", Service.apply)
  val writes = new SimpleObjectWrites[Service](_.id)
  val format = Format(reads, writes)
}

object Invitation {

  implicit val dateWrites = RestFormats.dateTimeWrite
  implicit val dateReads = RestFormats.dateTimeRead
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "service" -> invitation.service.id,
      "clientIdType" -> invitation.clientIdType,
      "clientId" -> invitation.clientId,
      "postcode" -> invitation.postcode,
      "arn" -> invitation.arn.value,
      "suppliedClientId" -> invitation.suppliedClientId,
      "suppliedClientIdType" -> invitation.suppliedClientIdType,
      "created" -> invitation.firstEvent().time,
      "lastUpdated" -> invitation.mostRecentEvent().time,
      "status" -> invitation.status
    )

  }

  implicit val serviceFormat = Service.format
  val mongoFormats = ReactiveMongoFormats.mongoEntity(Json.format[Invitation])
}

object AgentInvitation {
  implicit val format = Json.format[AgentInvitation]
}