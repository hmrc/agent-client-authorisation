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