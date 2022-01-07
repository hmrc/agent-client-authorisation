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

import org.joda.time.LocalDate
import play.api.libs.json._
import play.api.libs.json.JodaReads

case class PptSubscription(customerName: String, dateOfApplication: LocalDate, deregistrationDate: Option[LocalDate])

object PptSubscription {

  implicit val jodaReads = JodaReads.DefaultJodaLocalDateReads

  implicit def reads(json: JsValue): JsResult[PptSubscription] = {
    val dateOfApplication = (json \ "legalEntityDetails" \ "dateOfApplication").as[LocalDate]
    val deregistrationDate = (json \ "changeOfCircumstanceDetails" \ "deregistrationDetails" \ "deregistrationDate").asOpt[LocalDate]
    (json \ "legalEntityDetails" \ "customerDetails" \ "customerType").as[String] match {
      case "Individual" =>
        val firstName = (json \ "legalEntityDetails" \ "customerDetails" \ "individualDetails" \ "firstName").as[String]
        val lastName = (json \ "legalEntityDetails" \ "customerDetails" \ "individualDetails" \ "lastName").as[String]

        JsSuccess(PptSubscription(s"$firstName $lastName", dateOfApplication, deregistrationDate))

      case "Organisation" =>
        val organisationName = (json \ "legalEntityDetails" \ "customerDetails" \ "organisationDetails" \ "organisationName").as[String]
        JsSuccess(PptSubscription(organisationName, dateOfApplication, deregistrationDate))

      case e => JsError(s"unknown customerType $e")
    }
  }
}
