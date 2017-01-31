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

package uk.gov.hmrc.agentclientauthorisation.support

import org.scalatest.{Matchers, WordSpecLike}
import uk.gov.hmrc.agentclientauthorisation.model.MtdClientId
import uk.gov.hmrc.agentclientauthorisation.support.FakeMtdClientId._
import uk.gov.hmrc.domain.SaUtr

class FakeMtdClientIdSpec extends WordSpecLike with Matchers  {
  "toSaUtr" should {
    "perform fake transformation of a valid fake MTD client ID to an SA UTR" in {
      toSaUtr(MtdClientId("MTD-REG-5432154321")) shouldBe SaUtr("5432154321")
    }

    "throw IllegalArgumentException when the client MTD isn't a valid fake" in {
      intercept[IllegalArgumentException] {
        toSaUtr(MtdClientId("5432154321"))
      }
    }
  }

  "apply" should {
    "perform fake transformation of a SA UTR to a fake MTD client ID" in {
      FakeMtdClientId(SaUtr("5432154321")) shouldBe MtdClientId("MTD-REG-5432154321")
    }
  }
}
