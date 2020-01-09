/*
 * Copyright 2020 HM Revenue & Customs
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

import uk.gov.hmrc.play.test.UnitSpec

class UriPathEncodingSpec extends UnitSpec {
  "encodePathSegments" should {
    "separate segments with forward slashes" in {
      UriPathEncoding.encodePathSegments("one", "two", "three") shouldBe "/one/two/three"
    }

    "escape spaces using URL path encoding not form encoding" in {
      UriPathEncoding.encodePathSegments("AA1 1AA") shouldBe "/AA1%201AA"
    }

    "protect against path traversal attacks" in {
      UriPathEncoding.encodePathSegments("../bad", "ok") shouldBe "/..%2Fbad/ok"
    }
  }

  "encodePathSegment" should {
    "escape spaces using URL path encoding not form encoding" in {
      UriPathEncoding.encodePathSegment("AA1 1AA") shouldBe "AA1%201AA"
    }

    "protect against path traversal attacks" in {
      UriPathEncoding.encodePathSegment("../bad") shouldBe "..%2Fbad"
    }
  }
}
