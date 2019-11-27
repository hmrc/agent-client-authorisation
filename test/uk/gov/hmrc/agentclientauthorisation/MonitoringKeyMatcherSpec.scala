/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation

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

import play.api.Play
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.agentclientauthorisation.wiring.MonitoringKeyMatcher
import uk.gov.hmrc.play.test.UnitSpec

class MonitoringKeyMatcherSpec extends UnitSpec {

  "MonitoringKeyMatcher" should {

    "prepare patterns and variables" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq()
      }
      tested.preparePatternAndVariables("""/some/test/:service/:clientId/:test1""") shouldBe (
        (
          "^.*/some/test/([^/]+)/([^/]+)/([^/]+)$",
          Seq("{service}", "{clientId}", "{test1}")))
      tested.preparePatternAndVariables("""/some/test/:service/:clientId/:test1/""") shouldBe (
        (
          "^.*/some/test/([^/]+)/([^/]+)/([^/]+)/$",
          Seq("{service}", "{clientId}", "{test1}")))
      tested.preparePatternAndVariables("""/some/test/:service/::clientId/:test1/""") shouldBe (
        (
          "^.*/some/test/([^/]+)/([^/]+)/([^/]+)/$",
          Seq("{service}", "{:clientId}", "{test1}")))
      tested.preparePatternAndVariables("""/some/test/:service/clientId/:test1/""") shouldBe (
        (
          "^.*/some/test/([^/]+)/clientId/([^/]+)/$",
          Seq("{service}", "{test1}")))
    }

    "throw exception if duplicate variable name in pattern" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq()
      }
      an[IllegalArgumentException] shouldBe thrownBy {
        tested.preparePatternAndVariables("""/some/test/:service/:clientId/:service""")
      }
    }

    "match value to known pattern and produce key with placeholders replaced" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq(
          "A-{service}"            -> """/some/test/:service/:clientId""",
          "B-{service}"            -> """/test/:service/bar/some""",
          "C-{service}"            -> """/test/:service/bar""",
          "D-{service}"            -> """/test/:service/""",
          "E-{clientId}-{service}" -> """/test/:service/:clientId"""
        )
      }
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME/bar") shouldBe Some("C-ME")
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME/bar/some") shouldBe Some("B-ME")
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME") shouldBe None
      tested.findMatchingKey("/some/test/ME/12616276") shouldBe Some("A-ME")
      tested.findMatchingKey("http://www.tax.service.hmrc.gov.uk/test/ME/TOO") shouldBe Some("E-TOO-ME")
      tested.findMatchingKey("/test/ME/TOO/") shouldBe None
    }

    "match URI to known pattern and produce key with placeholders replaced" in {
      val tested = new MonitoringKeyMatcher {
        override val keyToPatternMapping: Seq[(String, String)] = Seq(
          "foo-{service}"        -> "/foo/agent/:arn/service/:service/client/:clientId",
          "foo-PIR"              -> "/foo/PERSONAL-INCOME-RECORD/agent/:arn/client/:clientId",
          "foo-AFI"              -> "/foo/afi/agent/:arn/client/:clientId",
          "client-foo-{service}" -> "/foo/service/:service/clientId/:clientId"
        )
      }
      tested.findMatchingKey(
        "http://foo.protected.mdtp/foo/agent/ARN123456/service/PERSONAL-INCOME-RECORD/client/GHZ8983HJ") shouldBe Some(
        "foo-PERSONAL-INCOME-RECORD")
      tested.findMatchingKey("http://foo.protected.mdtp/foo/PERSONAL-INCOME-RECORD/agent/ARN123456/client/GHZ8983HJ") shouldBe Some(
        "foo-PIR")
      tested.findMatchingKey("http://foo.protected.mdtp/foo/afi/agent/ARN123456/client/GHZ8983HJ") shouldBe Some(
        "foo-AFI")
      tested.findMatchingKey("http://foo.protected.mdtp/foo/service/PERSONAL-INCOME-RECORD/clientId/GHZ8983HJ") shouldBe Some(
        "client-foo-PERSONAL-INCOME-RECORD")
    }

    //    "parse Routes and produce key-pattern mappings" in {
    //      val app = GuiceApplicationBuilder().build()
    //      Play.start(app)
    //      val tested = new MonitoringKeyMatcher {
    //        override val keyToPatternMapping: Seq[(String, String)] = KeyToPatternMappingFromRoutes(Set())
    //      }
    //      tested.findMatchingKey("http://agent-client-authorisation.protected.mdtp/agent-client-authorisation//clients/NI/ABC123456/invitations/received/A634764HHJJH/accept") shouldBe Some("|clients|NI|:|invitations|received|:|accept")
    //      await(app.stop())
    //    }

  }
}
