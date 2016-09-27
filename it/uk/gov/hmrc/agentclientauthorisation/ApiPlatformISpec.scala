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

package uk.gov.hmrc.agentclientauthorisation

import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class ApiPlatformISpec extends UnitSpec with MongoAppAndStubs {
  "/api/definition" should {
    "return the defintion JSON" in {
      val response: HttpResponse = new Resource(s"/agent-client-authorisation/api/definition", port).get()
      response.status shouldBe 200

      val definition = response.json
      (definition \ "api" \ "name").as[String] shouldBe "Agent-client Authorisation"
    }
  }
}
