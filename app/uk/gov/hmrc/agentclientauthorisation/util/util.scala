/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.mvc.Result

import scala.concurrent.Future

package object util {

  def toFuture[A](a: A): Future[A] = Future.successful(a)

  def failure[A](r: Result): Future[A] = Future failed FailedResultException(r)

  implicit class throwableOps(val t: Throwable) extends AnyVal {
    def toFailure[A]: Future[A] = Future.failed(t)
  }
  implicit class valueOps[A](val a: A) extends AnyVal {
    def toFuture: Future[A] = Future.successful(a)
  }
}
