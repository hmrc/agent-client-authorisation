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

package uk.gov.hmrc.agentclientauthorisation.util

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

import java.time.{Instant, ZoneOffset, ZonedDateTime}

import play.api.libs.json._
import reactivemongo.bson.BSONObjectID

import scala.util.{Failure, Success}

trait JsonReactiveMongoFormats {

  private val utcOffset = ZoneOffset.UTC

  implicit val dateTimeRead: Reads[ZonedDateTime] =
    (__ \ "$date").read[Long].map { dateTime =>
      ZonedDateTime.ofInstant(Instant.ofEpochMilli(dateTime), utcOffset)
    }

  implicit val dateTimeWrite: Writes[ZonedDateTime] = new Writes[ZonedDateTime] {
    def writes(dateTime: ZonedDateTime): JsValue = Json.obj(
      "$date" -> dateTime.toInstant.toEpochMilli
    )
  }

  implicit val objectIdRead: Reads[BSONObjectID] = Reads[BSONObjectID] { json =>
    (json \ "$oid").validate[String].flatMap { str =>
      BSONObjectID.parse(str) match {
        case Success(bsonId) => JsSuccess(bsonId)
        case Failure(err)    => JsError(__, s"Invalid BSON Object ID $json; ${err.getMessage}")
      }
    }
  }

  implicit val objectIdWrite: Writes[BSONObjectID] = new Writes[BSONObjectID] {
    def writes(objectId: BSONObjectID): JsValue = Json.obj(
      "$oid" -> objectId.stringify
    )
  }

  implicit val objectIdFormats = Format(objectIdRead, objectIdWrite)
  implicit val dateTimeFormats = Format(dateTimeRead, dateTimeWrite)

  def mongoEntity[A](baseFormat: Format[A]): Format[A] = {
    import uk.gov.hmrc.mongo.json.JsonExtensions._
    val publicIdPath: JsPath = JsPath \ '_id
    val privateIdPath: JsPath = JsPath \ 'id
    new Format[A] {
      def reads(json: JsValue): JsResult[A] = baseFormat.compose(copyKey(publicIdPath, privateIdPath)).reads(json)

      def writes(o: A): JsValue = baseFormat.transform(moveKey(privateIdPath, publicIdPath)).writes(o)
    }
  }
}

object JsonReactiveMongoFormats extends JsonReactiveMongoFormats
