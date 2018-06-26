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

package uk.gov.hmrc.agentclientauthorisation.repository

import reactivemongo.api.indexes.Index
import reactivemongo.core.errors.GenericDatabaseException
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

trait StrictlyEnsureIndexes[A <: Any, ID <: Any] {

  self: ReactiveRepository[A, ID] =>

  private def ensureIndexOrFail(index: Index)(implicit ec: ExecutionContext): Future[Boolean] = {
    val indexInfo = s"""${index.eventualName}, key=${index.key
      .map { case (k, _) => k }
      .mkString("+")}, unique=${index.unique}, background=${index.background}, sparse=${index.sparse}"""
    collection.indexesManager
      .create(index)
      .map(wr => {
        if (wr.ok) {
          logger.info(s"Successfully Created Index ${collection.name}.$indexInfo")
          true
        } else {
          val msg = wr.writeErrors.mkString(", ")
          if (msg.contains("E11000")) {
            // this is for backwards compatibility to mongodb 2.6.x
            throw GenericDatabaseException(msg, wr.code)
          } else {
            throw new IllegalStateException(s"Failed to ensure index $indexInfo, error=$msg")
          }
        }
      })
      .recover {
        case t =>
          logger.error(message, t)
          false
      }
  }

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    Future.sequence(indexes.map(ensureIndexOrFail))

}
