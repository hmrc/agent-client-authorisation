/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.{Format, JsResult, JsSuccess, JsValue, Json}

import java.time.{Instant, LocalDateTime, ZoneOffset}

case class StatusChangeEvent(time: LocalDateTime, status: InvitationStatus)

object StatusChangeEvent {
  implicit val statusChangeEventFormat: Format[StatusChangeEvent] = new Format[StatusChangeEvent] {
    override def reads(json: JsValue): JsResult[StatusChangeEvent] = {
      val time = Instant.ofEpochMilli((json \ "time").as[Long]).atZone(ZoneOffset.UTC).toLocalDateTime
      val status = InvitationStatus((json \ "status").as[String])
      JsSuccess(StatusChangeEvent(time, status))
    }

    override def writes(o: StatusChangeEvent): JsValue =
      Json.obj(
        "time"   -> o.time.toInstant(ZoneOffset.UTC).toEpochMilli,
        "status" -> o.status.toString
      )
  }
}
