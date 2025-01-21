/*
 * Copyright 2025 HM Revenue & Customs
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

import org.apache.pekko.Done
import play.api.Logging
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.DurationInt
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class LockClient @Inject() (mongoLockRepository: MongoLockRepository)(implicit ec: ExecutionContext) extends Logging {
  private val lockService = LockService(mongoLockRepository, lockId = "migration-lock", ttl = 6.hour)
  def migrateWithLock(body: => Future[Done]): Future[Unit] = {
    logger.warn("Attempting to take lock for migration...")
    lockService
      .withLock(body)
      .map {
        case Some(res) => logger.warn(s"Finished with $res. Lock has been released.")
        case None      => logger.warn("Failed to take lock")
      }
  }
}
