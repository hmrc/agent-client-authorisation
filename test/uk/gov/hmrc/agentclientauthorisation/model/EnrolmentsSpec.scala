/*
 * Copyright 2017 HM Revenue & Customs
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

import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.test.UnitSpec

class EnrolmentsSpec extends UnitSpec {

  "arnOption" should {
    "return None" when {
      "no HMRC-AS-AGENT enrolment is found" in {
        Enrolments(Set()).arnOption shouldBe None
      }

      "there's no AgentReferenceNumber in HMRC-AS-AGENT enrolment" in {
        Enrolments(Set(AuthEnrolment("HMRC-AS-AGENT", Seq(), "Pending"))).arnOption shouldBe None
      }
    }

    "return Some(agent reference)" when {
      "there is AgentReferenceNumber in HMRC-AS-AGENT enrolment" in {
        val authEnrolment = AuthEnrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", "TARN0000001")), "Activated")
        Enrolments(Set(authEnrolment)).arnOption shouldBe Some(Arn("TARN0000001"))
      }

      "there is AgentReferenceNumber in HMRC-AS-AGENT enrolment, but the enrolment is pending" in {
        val authEnrolment = AuthEnrolment("HMRC-AS-AGENT", Seq(EnrolmentIdentifier("AgentReferenceNumber", "TARN0000001")), "Pending")
        Enrolments(Set(authEnrolment)).arnOption shouldBe Some(Arn("TARN0000001"))
      }
    }
  }

}
