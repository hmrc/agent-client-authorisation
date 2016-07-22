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

package uk.gov.hmrc.agentclientauthorisation.sa.connectors

import uk.gov.hmrc.play.test.UnitSpec

class CesaDesignatoryDetailsNameSpec extends UnitSpec {
  "toString" should {
    "return all elements separated by space when all are present" in {
      val name: CesaDesignatoryDetailsName = CesaDesignatoryDetailsName(Some("Ms"), Some("T'ax"), Some("Payer"))
      name.toString shouldBe "Ms T'ax Payer"
    }

    "return populated elements separated by a single space when some but not all are present" in {
      val name: CesaDesignatoryDetailsName = CesaDesignatoryDetailsName(Some("Ms"), None, Some("Payer"))
      name.toString shouldBe "Ms Payer"
    }

    "return populated elements separated by a single space when only one is present" in {
      val name: CesaDesignatoryDetailsName = CesaDesignatoryDetailsName(None, None, Some("Payer"))
      name.toString shouldBe "Payer"
    }

    "return empty string when no elements are present" in {
      val name: CesaDesignatoryDetailsName = CesaDesignatoryDetailsName(None, None, None)
      name.toString shouldBe ""
    }
  }
}
