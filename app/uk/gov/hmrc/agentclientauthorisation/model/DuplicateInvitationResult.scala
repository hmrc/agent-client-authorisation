/*
 * Copyright 2024 HM Revenue & Customs
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

import org.mongodb.scala.Document
import org.mongodb.scala.bson.BsonInt32
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads}

case class DuplicateInvitationResult(toDelete: Int, invitationDetails: InvitationDetails)

object DuplicateInvitationResult {

  def fromDocument(doc: Document): DuplicateInvitationResult = {

    val counter = doc.get[BsonInt32]("counter").map(_.getValue).getOrElse(throw new Exception("no counter!"))

    val toDelete = counter - 1 // leave one invitation

    val invitationDetails = Json.parse(doc.toSeq.head._2.toString).as[InvitationDetails]

    DuplicateInvitationResult(toDelete, invitationDetails)

  }
}

case class InvitationDetails(arn: String, suppliedClientId: String, service: String)

object InvitationDetails {

  implicit val reads: Reads[InvitationDetails] = (
    (JsPath \ "_key" \ "arnValue").read[String] and
      (JsPath \ "_key" \ "suppliedClientIdValue").read[String] and
      (JsPath \ "_key" \ "serviceValue").read[String]
  )(InvitationDetails.apply _)

}
