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

package uk.gov.hmrc.agentclientauthorisation.controllers.api

import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.language.postfixOps

class ApiPlatformISpec extends UnitSpec with MongoAppAndStubs {


  "/public/api/definition" should {
    "return the definition JSON" in {
      val response: HttpResponse = new Resource(s"/api/definition", port).get()
      response.status shouldBe 200

      val definition = response.json

      (definition \ "api" \ "name").as[String] shouldBe "Agent Client Authorisation"

      val accessConfig = definition \ "api" \ "versions" \\ "access"
      (accessConfig.head \ "type").as[String] shouldBe "PRIVATE"
      (accessConfig.head \ "whitelistedApplicationIds").head.as[String] shouldBe "00010002-0003-0004-0005-000600070008"
      (accessConfig.head \ "whitelistedApplicationIds")(1).as[String] shouldBe "00090002-0003-0004-0005-000600070008"
    }
  }


  "provide RAML documentation exists for all API versions" in new ApiTestSupport {

    lazy override val runningPort: Int = port

    forAllApiVersions(ramlByVersion) { case (version, raml) =>

      info(s"Checking API RAML documentation for version[$version] of the API")

      withClue("RAML does not contain a valid RAML 1.0 version header") {
        raml should include("#%RAML 1.0")
      }

      withClue("RAML does not contain the title 'Agent Client Authorisation API'") {
        raml should include("title: Agent Client Authorisation")

      }

      withClue(s"RAML does not contain a matching version declaration of [$version]") {
        raml should include(s"version: $version")
      }
    }
  }
}
