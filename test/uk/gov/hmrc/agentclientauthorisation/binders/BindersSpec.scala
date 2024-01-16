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

package uk.gov.hmrc.agentclientauthorisation.binders

import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.binders.Binders.{InvitationStatusBinder, LocalDateBinder, LocalDateQueryStringBinder}
import uk.gov.hmrc.agentclientauthorisation.model.Accepted
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Utr}

import java.time.LocalDate

class BindersSpec extends UnitSpec {

  "InvitationStatusBinder" should {
    "only consider the first argument" in {
      InvitationStatusBinder.bind("status", Map("status" -> Seq("Accepted", "Pending"))) shouldBe Some(Right(Accepted))
    }

    "reject unknown status" in {
      InvitationStatusBinder.bind("status", Map("status" -> Seq("NotAStatus"))) shouldBe Some(
        Left("Cannot parse parameter status as InvitationStatus: status of [NotAStatus] is not a valid InvitationStatus"))
    }

    "reject zero status arguments" in {
      InvitationStatusBinder.bind("status", Map("status" -> Seq())) shouldBe None
    }
  }

  "LocalDateBinder" should {
    "accept bind of valid dates" in {
      LocalDateBinder.bind("date", "2001-01-02") shouldBe Right(LocalDate.parse("2001-01-02"))
    }
    "reject bind of invalid dates" in {
      LocalDateBinder.bind("date", "01-01-02").isLeft shouldBe true
    }
    "unbind" in {
      LocalDateBinder.unbind("date", LocalDate.parse("2001-01-02")) shouldBe "2001-01-02"
    }
  }

  "LocalDateQueryStringBinder" should {
    "accept bind of valid dates" in {
      LocalDateQueryStringBinder.bind("date", Map("date" -> Seq("2001-01-02"))).get shouldBe Right(LocalDate.parse("2001-01-02"))
    }
    "reject bind of invalid dates" in {
      LocalDateQueryStringBinder.bind("date", Map("date" -> Seq("01-01-02"))).get.isLeft shouldBe true
    }
    "reject bind of missing date" in {
      LocalDateQueryStringBinder.bind("date", Map("foo" -> Seq("bar"))).isEmpty shouldBe true
    }
    "unbind" in {
      LocalDateQueryStringBinder.unbind("date", LocalDate.parse("2001-01-02")) shouldBe "2001-01-02"
    }
  }

  "AgentIdBinder" should {
    "accept a valid ARN" in {
      Binders.agentIdBinder.bind("agentId", "EARN8077593").toOption.get shouldBe Right(Arn("EARN8077593"))
    }
    "accept a valid UTR" in {
      Binders.agentIdBinder.bind("agentId", "2134514321").toOption.get shouldBe Left(Utr("2134514321"))
    }
    "fail if an invalid identifier is provided" in {
      Binders.agentIdBinder.bind("agentId", "XXCGT1029384756").isLeft shouldBe true
    }
  }
}
