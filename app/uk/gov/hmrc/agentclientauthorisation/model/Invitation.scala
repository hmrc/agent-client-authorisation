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

import java.nio.charset.Charset
import java.security.MessageDigest

import org.joda.time.{DateTime, DateTimeZone}
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.CLIENT_ID_TYPE_NINO
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
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
    case _ => None
  }

  def apply(status: String): InvitationStatus = status.toLowerCase match {
    case "pending" => Pending
    case "rejected" => Rejected
    case "accepted" => Accepted
    case "cancelled" => Cancelled
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

case class InvitationId(value: String) {
  require(value.size == 10, "The size of invitation id should not exceed 10")
}

object InvitationId {

  private def idWrites = (__ \ "value")
    .write[String]
    .contramap((id: InvitationId) => id.value.toString)

  private def idReads = (__ \ "value")
    .read[String]
    .map(x => InvitationId(x))

  implicit val idFormats = Format(idReads, idWrites)

  private[model] def byteToBitsLittleEndian(byte: Byte): Seq[Boolean] = {
    def isBitOn(bitPos: Int): Boolean = {
      val maskSingleBit = 0x01 << bitPos
      (byte & maskSingleBit) != 0
    }

    (0 to 7).map(isBitOn)
  }

  private[model] def to5BitNum(bitsLittleEndian: Seq[Boolean]) = {
    require(bitsLittleEndian.size == 5)

    bitsLittleEndian
      .take(5)
      .zipWithIndex
      .map{ case (bit, power) => if(bit) 1 << power else 0 }
      .sum
  }

  private[model] def bytesTo5BitNums(bytes: Seq[Byte]): Seq[Int] = {
    require(bytes.size % 5 == 0)
    require(bytes.nonEmpty)

    bytes.flatMap(byteToBitsLittleEndian).grouped(5).map(to5BitNum).toSeq
  }

  private[model] def to5BitAlphaNumeric(fiveBitNum: Int) = {
    require(fiveBitNum >= 0 && fiveBitNum <= 31)

    "ABCDEFGHJKLMNOPRSTUWXYZ123456789"(fiveBitNum)
  }

  private[model] def sha256(bytes: Array[Byte]) = MessageDigest.getInstance("SHA-256").digest(bytes)

  def create(arn: Arn,
             clientId: MtdItId,
             serviceName: String,
             timestamp: DateTime = DateTime.now(DateTimeZone.UTC))(prefix: Char): InvitationId = {
    val idUnhashed = s"${arn.value}.${clientId.value},$serviceName-${timestamp.getMillis}"
    val idBytes = sha256(idUnhashed.getBytes("UTF-8")).take(5)
    val idChars = bytesTo5BitNums(idBytes).map(to5BitAlphaNumeric).mkString
    val idWithPrefix = s"$prefix$idChars"
    val checkDigit = to5BitAlphaNumeric(CRC5.calculate(idWithPrefix))

    InvitationId(s"$idWithPrefix$checkDigit")
  }
}

case class Invitation(
                       id: BSONObjectID,
                       invitationId: InvitationId,
                       arn: Arn,
                       service: String,
                       clientId: String,
                       postcode: String,
                       suppliedClientId: String,
                       suppliedClientIdType: String,
                       events: List[StatusChangeEvent]) {

  val clientIdType = CLIENT_ID_TYPE_NINO

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
                            clientPostcode: String)

object StatusChangeEvent {
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
}

object Invitation {
  implicit val dateWrites = RestFormats.dateTimeWrite
  implicit val dateReads = RestFormats.dateTimeRead
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats
  implicit val jsonWrites = new Writes[Invitation] {
    def writes(invitation: Invitation) = Json.obj(
      "service" -> invitation.service,
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
  val mongoFormats = ReactiveMongoFormats.mongoEntity(Json.format[Invitation])
}

object AgentInvitation {
  implicit val format = Json.format[AgentInvitation]
}


object CRC5 {

  /* Params for CRC-5/EPC */
  val bitWidth = 5
  val poly = 0x09
  val initial = 0x09
  val xorOut = 0

  val table: Seq[Int] = {
    val widthMask = (1 << bitWidth) - 1
    val shpoly = poly << (8 - bitWidth)
    for (i <- 0 until 256) yield {
      var crc = i
      for (_ <- 0 until 8) {
        crc = if ((crc & 0x80) != 0) (crc << 1) ^ shpoly else crc << 1
      }
      (crc >> (8 - bitWidth)) & widthMask
    }
  }

  val ASCII = Charset.forName("ASCII")

  def calculate(string: String): Int = calculate(string.getBytes())

  def calculate(input: Array[Byte]): Int = {
    val start = 0
    val length = input.length
    var crc = initial ^ xorOut
    for (i <- 0 until length) {
      crc = table((crc << (8 - bitWidth)) ^ (input(start + i) & 0xff)) & 0xff
    }
    crc ^ xorOut
  }
}
