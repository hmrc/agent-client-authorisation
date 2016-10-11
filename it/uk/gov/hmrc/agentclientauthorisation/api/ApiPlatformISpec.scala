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

package uk.gov.hmrc.agentclientauthorisation.api

import play.utils.UriEncoding
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

    forAllApiVersions() { case (version, endpoints) =>

      info(s"Checking API XML documentation for version[$version] of the API")

      endpoints foreach { endpoint =>

        info(s"$version - ${endpoint.endPointName}")

        withClue(s"\ndefinitions specifies endpoint '${endpoint endPointName}', it does not exist \ncheck the name in the corresponding XML documentation?)\n") {
          val response = documentationFor(endpoint)
          response.status shouldBe 200
        }
      }
    }
  }
}
