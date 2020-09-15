package uk.gov.hmrc.agentclientauthorisation.util

import uk.gov.hmrc.play.test.UnitSpec
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
