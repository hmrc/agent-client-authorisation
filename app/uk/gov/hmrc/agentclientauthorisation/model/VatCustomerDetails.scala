/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.functional.syntax._
import play.api.libs.json._

case class CustomerDetailsNotFound() extends Exception

case class VatCustomerDetails(organisationName: Option[String], individual: Option[VatIndividual], tradingName: Option[String])

case class VatIndividual(title: Option[String], firstName: Option[String], middleName: Option[String], lastName: Option[String]) {
  def name: String =
    Seq(title, firstName, middleName, lastName).flatten.map(_.trim).filter(_.nonEmpty).mkString(" ")
}

object VatCustomerDetails {
  implicit val format: Format[VatCustomerDetails] = Json.format[VatCustomerDetails]
}

object VatIndividual {

  private val titles: Map[String, String] = Map(
    "0001" -> "Mr",
    "0002" -> "Mrs",
    "0003" -> "Miss",
    "0004" -> "Ms",
    "0005" -> "Dr",
    "0006" -> "Sir",
    "0007" -> "Rev",
    "0008" -> "Personal Representative of",
    "0009" -> "Professor",
    "0010" -> "Lord",
    "0011" -> "Lady",
    "0012" -> "Dame"
  )

  implicit val writes: OWrites[VatIndividual] = Json.writes[VatIndividual]

  implicit val reads: Reads[VatIndividual] = (
    (JsPath \ "title").readNullable[String].map(title => titles.get(title.getOrElse(""))) and
      (JsPath \ "firstName").readNullable[String] and
      (JsPath \ "middleName").readNullable[String] and
      (JsPath \ "lastName").readNullable[String]
  )(VatIndividual.apply _)

}
