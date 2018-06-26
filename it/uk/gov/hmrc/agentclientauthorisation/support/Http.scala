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

import play.api.Play.current
import play.api.http.{HeaderNames, MimeTypes}
import play.api.libs.ws.{WS, WSRequest, WSResponse}
import play.api.mvc.Results
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.ws.WSHttpResponse

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object Http {

  def get(url: String)(implicit hc: HeaderCarrier): HttpResponse =
    perform(url) { request =>
      request.get()
    }

  def post(url: String, body: String, headers: Seq[(String, String)] = Seq.empty)(
    implicit hc: HeaderCarrier): HttpResponse = perform(url) { request =>
    request.withHeaders(headers: _*).post(body)
  }

  def postEmpty(url: String)(implicit hc: HeaderCarrier): HttpResponse =
    perform(url) { request =>
      import play.api.http.Writeable._
      request.post(Results.EmptyContent())
    }

  def putEmpty(url: String)(implicit hc: HeaderCarrier): HttpResponse =
    perform(url) { request =>
      import play.api.http.Writeable._
      request.put(Results.EmptyContent())
    }

  def delete(url: String)(implicit hc: HeaderCarrier): HttpResponse =
    perform(url) { request =>
      request.delete()
    }

  private def perform(url: String)(fun: WSRequest => Future[WSResponse])(implicit hc: HeaderCarrier): WSHttpResponse =
    await(
      fun(
        WS.url(url)
          .withHeaders(hc.headers: _*)
          .withRequestTimeout(20000 milliseconds)).map(new WSHttpResponse(_)))

  private def await[A](future: Future[A]) =
    Await.result(future, Duration(10, SECONDS))

}

class Resource(path: String, port: Int) {

  private def url() = s"http://localhost:$port$path"

  def get()(implicit hc: HeaderCarrier = HeaderCarrier()) = Http.get(url)(hc)

  def postAsJson(body: String)(implicit hc: HeaderCarrier = HeaderCarrier()) =
    Http.post(url, body, Seq(HeaderNames.CONTENT_TYPE -> MimeTypes.JSON))(hc)

  def postEmpty()(implicit hc: HeaderCarrier = HeaderCarrier()) =
    Http.postEmpty(url)(hc)

  def putEmpty()(implicit hc: HeaderCarrier = HeaderCarrier()) =
    Http.putEmpty(url)(hc)
}
