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

package uk.gov.hmrc.agentclientauthorisation.controllers.api

import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.language.postfixOps

class ApiPlatformISpec extends UnitSpec with MongoAppAndStubs {


  "/public/api/definition" should {
    "return the definition JSON" in {
      val response: HttpResponse = new Resource(s"/api/definition", port).get()
      response.status shouldBe 200

      val definition = response.json

      (definition \ "api" \ "name").as[String] shouldBe "Agent-client Authorisation"
    }
  }


  "provide XML documentation for all endpoints in the definitions file" in new ApiTestSupport {

    lazy override val runningPort: Int = port

    forAllApiVersions(endpointsByVersion) { case (version, endpoints) =>

      info(s"Checking API XML documentation for version[$version] of the API")

      endpoints foreach { endpoint =>

        val endpointName: String = endpoint.endPointName

        info(s"$version - $endpointName")

        val (status, contents) = xmlDocumentationFor(endpoint)

        withClue(s"definitions specifies endpoint '$endpointName', is there a ${endpointName.replaceAll(" ", "-")}.xml file?") {
          status shouldBe 200
        }

        withClue(s"documentation for '$endpointName' should be well formed XML with a corresponding 'name' element") {
          contents.isSuccess shouldBe true
          (contents.get \ "name").head.text shouldBe endpointName
        }
      }
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