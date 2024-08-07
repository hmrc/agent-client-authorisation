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

package uk.gov.hmrc.agentclientauthorisation

import javax.inject.Inject
import play.api.http.{DefaultHttpRequestHandler, HttpConfiguration, HttpErrorHandler, HttpFilters}
import play.api.mvc.request.RequestTarget
import play.api.mvc.{Handler, RequestHeader}
import play.api.routing.Router
import play.core.DefaultWebCommands

/** Normalise the request path. The API platform strips the context '/agent-client-authorisation' from the URL before forwarding the request. Re-add
  * it here if necessary.
  */
class ApiPlatformRequestHandler @Inject() (router: Router, errorHandler: HttpErrorHandler, configuration: HttpConfiguration, filters: HttpFilters)
    extends DefaultHttpRequestHandler(new DefaultWebCommands(), None, router, errorHandler, configuration, filters.filters) {

  override def handlerForRequest(request: RequestHeader): (RequestHeader, Handler) =
    if (isApiPlatformRequest(request)) {
      super.handlerForRequest(
        request.withTarget(RequestTarget(path = addApiPlatformContext(request.path), uriString = request.uri, queryString = request.queryString))
      )
    } else {
      super.handlerForRequest(request)
    }

  private def addApiPlatformContext(path: String) = {
    val context = "/agent-client-authorisation"
    if (path == "/") {
      // special case for root - /agent-client-authorisation/ results in a 404
      // so we need to make it /agent-client-authorisation instead
      context
    } else {
      context + path
    }
  }

  private def isApiPlatformRequest(request: RequestHeader): Boolean =
    request.path.startsWith("/sandbox") ||
      request.path.startsWith("/agencies") ||
      request.path.startsWith("/clients") ||
      request.path == "/"

}
