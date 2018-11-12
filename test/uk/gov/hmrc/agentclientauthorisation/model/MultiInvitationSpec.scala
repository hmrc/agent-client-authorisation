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

import org.joda.time.DateTime
import play.api.libs.json.Json.toJson
import uk.gov.hmrc.agentclientauthorisation.repository.MultiInvitationRecord
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.play.test.UnitSpec

class MultiInvitationSpec extends UnitSpec {
  "MultiInvitationRecord" should {
    "serialize and deserialize from and to json" in {
      val created = DateTime.now()
      val expiryDate = DateTime.now().plusDays(10)

      val multiInvitation = MultiInvitationRecord(
        "12345678",
        Arn("ABCDEF123456"),
        Seq(InvitationId("ABBBBBBBBBBCA"), InvitationId("ABBBBBBBBBBCB"), InvitationId("ABBBBBBBBBBCC")),
        "personal",
        created
      )

      val json = toJson(multiInvitation)

      val result = json.as[MultiInvitationRecord]

      result.uid shouldBe multiInvitation.uid
      result.arn shouldBe multiInvitation.arn
      result.invitationIds shouldBe multiInvitation.invitationIds
      result.clientType shouldBe multiInvitation.clientType
      result.createdDate shouldBe multiInvitation.createdDate
    }
  }
}
