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
import uk.gov.hmrc.agentclientauthorisation.repository.{MultiInvitationRecord, ReceivedMultiInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.play.test.UnitSpec

class ReceivedMultiInvitationSpec extends UnitSpec {
  "ReceivedMultiInvitations" should {
    "serialize and deserialize from and to json" in {
      val created = DateTime.now()
      val expiryDate = DateTime.now().plusDays(10)

      val receivedMultiInvitation = ReceivedMultiInvitation(
        Arn("ABCDEF123456"),
        "My agent name",
        "personal",
        Seq(InvitationId("ABBBBBBBBBBCA"), InvitationId("ABBBBBBBBBBCB"), InvitationId("ABBBBBBBBBBCC"))
      )

      val json = toJson(receivedMultiInvitation)

      val result = json.as[ReceivedMultiInvitation]

      result.arn shouldBe receivedMultiInvitation.arn
      result.agentName shouldBe receivedMultiInvitation.agentName
      result.invitationIds shouldBe receivedMultiInvitation.invitationIds
      result.clientType shouldBe receivedMultiInvitation.clientType
    }

    "normalise agencyName" in {
      ReceivedMultiInvitation.normalizeAgentName("My age%*&nt name") shouldBe "my-agent-name"
      ReceivedMultiInvitation.normalizeAgentName("Paul  Tan 8 ()^£$???") shouldBe "paul-tan-8-"
    }
  }
}
