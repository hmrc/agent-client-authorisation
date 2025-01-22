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

import play.api.libs.json.Json.toJson
import uk.gov.hmrc.agentclientauthorisation.model.Invitation.external._
import uk.gov.hmrc.agentclientauthorisation.support.{TestConstants, TestData, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Service}

import java.time.LocalDateTime

class InvitationSpec extends UnitSpec with TestData {
  "Dates in the serialised JSON" should {
    "be in ISO8601 format" in {
      val created = "2010-01-01T01:00:23.456"
      val lastUpdated = "2010-01-02T04:00:23.456"

      val createdDateTime = LocalDateTime.parse(created)
      val lastUpdatedDateTime = LocalDateTime.parse(lastUpdated)

      val invitation = TestConstants.defaultInvitation.copy(
        invitationId = InvitationId("ABBBBBBBBBBCC"),
        arn = Arn("myAgency"),
        service = Service.MtdIt,
        events = List(StatusChangeEvent(createdDateTime, Pending), StatusChangeEvent(lastUpdatedDateTime, Accepted))
      )
      val json = toJson(invitation)

      implicit val format = MongoLocalDateTimeFormat.localDateTimeFormat

      (json \ "created").as[LocalDateTime] shouldBe createdDateTime
      (json \ "lastUpdated").as[LocalDateTime] shouldBe lastUpdatedDateTime
    }
  }
}
