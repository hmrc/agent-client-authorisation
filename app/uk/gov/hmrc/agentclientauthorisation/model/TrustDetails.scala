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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.libs.json.{Format, Json}

case class TrustAddress(
  line1: String,
  line2: String,
  line3: Option[String] = None,
  line4: Option[String] = None,
  postcode: Option[String] = None,
  country: String)

object TrustAddress {
  implicit val format: Format[TrustAddress] = Json.format[TrustAddress]
}

//#API-1495
case class TrustDetails(utr: String, trustName: String, address: TrustAddress, serviceName: String)

object TrustDetails {
  implicit val format: Format[TrustDetails] = Json.format[TrustDetails]
}

case class TrustDetailsResponse(trustDetails: TrustDetails)

object TrustDetailsResponse {
  implicit val format: Format[TrustDetailsResponse] = Json.format[TrustDetailsResponse]
}
