/*
 * Copyright 2021 HM Revenue & Customs
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

import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import org.joda.time.LocalDate.parse

class DateUtilsSpec extends UnitSpec {

  "DateUtils" should {

    "format a date for display" in {
      val d1 = "2020-07-31"
      val d2 = "2020-02-28"
      val d3 = "2021-12-01"
      val d4 = "2021-01-02"

      DateUtils.displayDate(parse(d1)) shouldBe "31 July 2020"
      DateUtils.displayDate(parse(d2)) shouldBe "28 February 2020"
      DateUtils.displayDate(parse(d3)) shouldBe "1 December 2021"
      DateUtils.displayDate(parse(d4)) shouldBe "2 January 2021"

    }
  }

}
