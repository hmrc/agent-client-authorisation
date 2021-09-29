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

import akka.stream.Materializer
import com.fasterxml.jackson.databind.JsonMappingException
import org.scalatest.matchers.{MatchResult, Matcher}
import play.api.mvc.Result
import uk.gov.hmrc.http.HttpResponse
import play.api.test.Helpers._

import scala.concurrent.Future
import scala.util.Try

trait ErrorResultMatchers { this: UnitSpec =>

  implicit def materializer: Materializer

  class ErrorResultMatcher(expectedResult: Result) extends Matcher[HttpResponse] {
    override def apply(left: HttpResponse): MatchResult = {
      val expectedBodyJson = contentAsJson(Future.successful(expectedResult))
      val rawNegatedFailureMessage =
        s"""Response had expected status ${expectedResult.header.status} and body "$expectedBodyJson""""
      if (left.status != expectedResult.header.status) {
        MatchResult(
          false,
          s"""Response had status ${left.status} not expected status ${expectedResult.header.status}""",
          rawNegatedFailureMessage)
      } else {
        Try(left.json)
          .map(
            json =>
              MatchResult(
                json == expectedBodyJson,
                s"""Response had body "$json" not expected body "$expectedBodyJson""",
                rawNegatedFailureMessage))
          .recover {
            case e: JsonMappingException =>
              MatchResult(
                false,
                s"""Response had body "${left.body}" which did not parse as JSON due to exception:\n$e""",
                rawNegatedFailureMessage)
          }
          .get
      }
    }
  }

  def matchErrorResult(expectedResult: Result) = new ErrorResultMatcher(expectedResult)

}
