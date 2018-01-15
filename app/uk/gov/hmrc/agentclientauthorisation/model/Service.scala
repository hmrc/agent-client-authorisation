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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.libs.json.Format
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, VrnValidation}
import uk.gov.hmrc.domain._

sealed abstract class Service(val id: String,
                              val invitationIdPrefix: Char,
                              val enrolmentKey: String,
                              val supportedSuppliedClientIdType: ClientIdType[_<:TaxIdentifier],
                              val supportedClientIdType: ClientIdType[_<:TaxIdentifier],
                              val requiresKnownFactsCheck: Boolean) {

  override def equals(that: Any): Boolean =
    that match {
      case that: Service => this.id.equals(that.id)
      case _ => false
    }
}

object Service {

  case object MtdIt extends Service("HMRC-MTD-IT", 'A', "HMRC-MTD-IT", NinoType, MtdItIdType, true)

  case object PersonalIncomeRecord extends Service("PERSONAL-INCOME-RECORD", 'B', "HMRC-NI", NinoType, NinoType, false)

  case object Vat extends Service("HMRC-MTD-VAT", 'C', "HMRC-MTD-VAT", VrnType, VrnType, false)

  val values = Seq(MtdIt, PersonalIncomeRecord, Vat)
  def findById(id: String): Option[Service] = values.find(_.id == id)
  def forId(id: String): Service = findById(id).getOrElse(throw new Exception("Not a valid service"))

  def apply(id: String) = forId(id)
  def unapply(service: Service): Option[String] = Some(service.id)

  val reads = new SimpleObjectReads[Service]("id", Service.apply)
  val writes = new SimpleObjectWrites[Service](_.id)
  val format = Format(reads, writes)
}


sealed abstract class ClientIdType[T<:TaxIdentifier](val clazz: Class[T],
                                                     val id: String,
                                                     val enrolmentId: String,
                                                     val createUnderlying: (String) => T) {
  def isValid(value: String): Boolean
}

object ClientIdType {
  val supportedTypes = Seq(NinoType, MtdItIdType, VrnType)
  def forId(id: String) = supportedTypes.find(_.id == id).getOrElse(throw new IllegalArgumentException("Invalid id:" + id))
}

case object NinoType extends ClientIdType(classOf[Nino], "ni", "NINO", Nino.apply){
  override def isValid(value: String): Boolean = Nino.isValid(value)
}

case object MtdItIdType extends ClientIdType(classOf[MtdItId], "MTDITID", "MTDITID", MtdItId.apply) {
  override def isValid(value: String): Boolean = MtdItId.isValid(value)
}

case object VrnType extends ClientIdType(classOf[Vrn], "vrn", "MTDVATID", Vrn.apply) {
  override def isValid(value: String) = VrnValidation.isValid(value)
}


case class ClientIdentifier[T<:TaxIdentifier](underlying: T) {

  private val clientIdType = ClientIdType.supportedTypes.find(_.clazz == underlying.getClass)
    .getOrElse(throw new Exception("Invalid type for clientId " + underlying.getClass.getCanonicalName))

  val value: String = underlying.value
  val typeId: String = clientIdType.id
  val enrolmentId: String = clientIdType.enrolmentId

  override def toString: String = value
}

object ClientIdentifier {
  type ClientId = ClientIdentifier[_<:TaxIdentifier]

  def apply(value: String, typeId: String): ClientId = {
    ClientIdType.supportedTypes.find(_.id == typeId)
      .getOrElse(throw new IllegalArgumentException("Invalid Client Id Type: " + typeId))
      .createUnderlying(value)
  }

  implicit def wrap[T<:TaxIdentifier](taxId: T): ClientIdentifier[T] = ClientIdentifier(taxId)
}

