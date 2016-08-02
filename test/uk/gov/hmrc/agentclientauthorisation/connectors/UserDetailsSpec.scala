/*
 * Copyright 2016 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.play.test.UnitSpec

class UserDetailsSpec extends UnitSpec {
  "User details agentName" should {
    "return name and last name if both are specified" in {
      val details = UserDetails("1st", Some("last"), Some("Friendly Name"))

      details.agentName shouldBe "1st last"
    }

    "return name only if last name is not specified" in {
      val details = UserDetails("1st", None, Some("Friendly Name"))

      details.agentName shouldBe "1st"
    }

  }

}
