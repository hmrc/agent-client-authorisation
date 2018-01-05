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

package uk.gov.hmrc.agentclientauthorisation.binders

import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.binders.PathBinders.InvitationStatusBinder
import uk.gov.hmrc.agentclientauthorisation.model.Accepted

class PathBindersSpec extends UnitSpec {

  "InvitationStatusBinder" should {
    "only consider the first argument" in {
      InvitationStatusBinder.bind("status", Map("status" -> Seq("Accepted", "Pending"))) shouldBe Some(Right(Accepted))
    }

    "reject unknown status" in {
      InvitationStatusBinder.bind("status", Map("status" -> Seq("NotAStatus"))) shouldBe Some(Left("Cannot parse parameter status as InvitationStatus: status of [NotAStatus] is not a valid InvitationStatus"))
    }

    "reject zero status arguments" in {
      InvitationStatusBinder.bind("status", Map("status" -> Seq())) shouldBe None
    }
  }
}
