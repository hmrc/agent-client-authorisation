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

package uk.gov.hmrc.agentclientauthorisation.support

import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.{WSClient, WSRequest, WSResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import javax.inject.Inject
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class Http @Inject() (wsClient: WSClient) {

  def get(url: String)(implicit hc: HeaderCarrier): HttpResponse = perform(url) { request =>
    request.get()
  }

  def post(url: String, body: String, headers: Seq[(String, String)] = Seq.empty)(implicit hc: HeaderCarrier): HttpResponse = perform(url) {
    request =>
      request.withHttpHeaders(headers: _*).post(body)
  }

  def put(url: String, body: String, headers: Seq[(String, String)] = Seq.empty)(implicit hc: HeaderCarrier): HttpResponse = perform(url) { request =>
    request.withHttpHeaders(headers: _*).put(body)
  }

  def delete(url: String)(implicit hc: HeaderCarrier): HttpResponse = perform(url) { request =>
    request.delete()
  }

  private def perform(url: String)(fun: WSRequest => Future[WSResponse])(implicit hc: HeaderCarrier): HttpResponse =
    await(
      fun(wsClient.url(url).withHttpHeaders(hc.headers(Seq(hc.names.authorisation)): _*).withRequestTimeout(20000 milliseconds)).map(wsr =>
        HttpResponse(wsr.status, wsr.body)
      )
    )

  private def await[A](future: Future[A]) = Await.result(future, Duration(10, SECONDS))

}

class Resource(path: String, port: Int, http: Http) {

  private def url() = s"http://localhost:$port$path"

  def get()(implicit hc: HeaderCarrier = HeaderCarrier()) = http.get(url())(hc)

  def postAsJson(body: String)(implicit hc: HeaderCarrier = HeaderCarrier()) =
    http.post(url(), body, Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))(hc)

  def postEmpty()(implicit hc: HeaderCarrier = HeaderCarrier()) =
    http.post(url(), "")(hc)

  def putEmpty()(implicit hc: HeaderCarrier = HeaderCarrier()) =
    http.put(url(), "")(hc)
}
