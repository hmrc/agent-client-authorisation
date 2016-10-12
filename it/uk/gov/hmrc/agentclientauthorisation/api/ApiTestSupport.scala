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

import java.net.URL

import play.api.libs.json.JsValue
import play.utils.UriEncoding
import uk.gov.hmrc.agentclientauthorisation.api.ApiTestSupport.Endpoint
import uk.gov.hmrc.agentclientauthorisation.support.Resource
import uk.gov.hmrc.play.http.HttpResponse

object ApiTestSupport {
  case class Endpoint(uriPattern: String,
                      endPointName: String,
                      version:String
                     )
}

trait ApiTestSupport {

  val runningPort: Int
  val definitionPath = "/api/definition"
  val documentationPath = "/api/documentation"

  def definitionsJson: JsValue = {
    new Resource(definitionPath toString, runningPort).get().json
  }

  def documentationFor(endpoint:Endpoint): HttpResponse = {
    val endpointPath = s"${endpoint version}/${UriEncoding.encodePathSegment(endpoint endPointName, "UTF-8")}"
    new Resource(s"$documentationPath/$endpointPath", runningPort).get()
  }

  private val apiSection = (definitionsJson \ "api").as[JsValue]
  private val apiVersions: List[JsValue] = (apiSection \ "versions").as[List[JsValue]]

  private val endpointsByApiVersion: Map[String, List[Endpoint]] = apiVersions.map {
    versionOfApi => (versionOfApi \ "version").as[String] -> endpoints(versionOfApi)
  } toMap


  private def endpoints(version:JsValue) = {
    (version \ "endpoints").as[List[JsValue]].map { ep =>

      val uriPattern = (ep \ "uriPattern").as[String]
      val endPointName = (ep \ "endpointName").as[String]
      Endpoint( uriPattern, endPointName, (version \ "version").as[String])
    }
  }

  def forAllApiVersions()(run: ((String, List[ApiTestSupport.Endpoint])) => Unit): Unit =
    endpointsByApiVersion.foreach(run)
}
