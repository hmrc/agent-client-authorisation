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

import play.api.libs.json._

case class CgtAddressDetails(
  addressLine1: String,
  addressLine2: Option[String] = None,
  addressLine3: Option[String] = None,
  addressLine4: Option[String] = None,
  countryCode: String,
  postalCode: Option[String] = None)

case object CgtAddressDetails {
  implicit val format: Format[CgtAddressDetails] = Json.format[CgtAddressDetails]
}

case class IndividualName(firstName: String, lastName: String)

case object IndividualName {
  implicit val format: Format[IndividualName] = Json.format[IndividualName]
}

case class OrganisationName(name: String)

case object OrganisationName {
  implicit val format: Format[OrganisationName] = Json.format[OrganisationName]
}

case class TypeOfPersonDetails(typeOfPerson: String, name: Either[IndividualName, OrganisationName])

object TypeOfPersonDetails {
  implicit val format: Format[TypeOfPersonDetails] = new Format[TypeOfPersonDetails] {
    override def writes(tpd: TypeOfPersonDetails): JsValue = {
      val namePart = tpd.name match {
        case Left(individualName) =>
          s""""firstName": "${individualName.firstName}", "lastName": "${individualName.lastName}""""
        case Right(organisationName) =>
          s""""organisationName": "${organisationName.name}""""
      }

      Json.parse(s"""{
                    |"typeOfPerson": "${tpd.typeOfPerson}",
                    |$namePart
                    |}""".stripMargin)
    }

    override def reads(json: JsValue): JsResult[TypeOfPersonDetails] = {
      val typeOfPerson = (json \ "typeOfPerson").as[String]

      typeOfPerson match {

        case "Individual" =>
          val firstName = (json \ "firstName").as[String]
          val lastName = (json \ "lastName").as[String]
          JsSuccess(TypeOfPersonDetails("Individual", Left(IndividualName(firstName, lastName))))

        case "Trustee" =>
          val orgName = (json \ "organisationName").as[String]
          JsSuccess(TypeOfPersonDetails("Trustee", Right(OrganisationName(orgName))))

        case e => JsError(s"unexpected typeOfPerson from DES for CGT: $e")
      }

    }
  }
}

case class SubscriptionDetails(typeOfPersonDetails: TypeOfPersonDetails, addressDetails: CgtAddressDetails)

object SubscriptionDetails {
  implicit val format: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]
}

case class CgtSubscription(regime: String, subscriptionDetails: SubscriptionDetails)

object CgtSubscription {
  implicit val format: Format[CgtSubscription] = Json.format[CgtSubscription]
}

case class CgtError(httpResponseCode: Int, errors: Seq[DesError])

object CgtError {
  implicit val format: Format[CgtError] = Json.format[CgtError]
}

case class CgtSubscriptionResponse(response: Either[CgtError, CgtSubscription])
