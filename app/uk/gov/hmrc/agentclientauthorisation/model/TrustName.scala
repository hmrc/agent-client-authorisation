/*
 * Copyright 2020 HM Revenue & Customs
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

case class TrustName(name: String)

object TrustName {
  implicit val format: Format[TrustName] = Json.format[TrustName]
}

case class InvalidTrust(code: String, reason: String)

object InvalidTrust {
  implicit val format: Format[InvalidTrust] = Json.format[InvalidTrust]
}

case class TrustResponse(response: Either[InvalidTrust, TrustName])

object TrustResponse {
  implicit val format: Format[TrustResponse] = new Format[TrustResponse] {
    override def writes(trustResponse: TrustResponse): JsValue = trustResponse.response match {
      case Right(trustName)   => Json.toJson(trustName)
      case Left(invalidTrust) => Json.toJson(invalidTrust)
    }

    override def reads(json: JsValue): JsResult[TrustResponse] =
      json.asOpt[TrustName] match {
        case Some(name) => JsSuccess(TrustResponse(Right(name)))
        case None       => JsSuccess(TrustResponse(Left(json.as[InvalidTrust])))
      }
  }
}
