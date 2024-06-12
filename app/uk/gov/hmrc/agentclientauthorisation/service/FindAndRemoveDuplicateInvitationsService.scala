/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.service

import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.mongo.lock.{LockService, MongoLockRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.util.Random
@Singleton
class FindAndRemoveDuplicateInvitationsService @Inject()(
  invitationsRepository: InvitationsRepository,
  mongoLockRepository: MongoLockRepository,
  appConfig: AppConfig)
    extends Logging {

  private val LOCK_ID = "startup-lock"

  private val enabled = appConfig.startupMongoQueryEnabled

  Thread.sleep(Random.nextLong(2000))

  if (enabled) {
    val lockService: LockService = LockService(mongoLockRepository, lockId = LOCK_ID, ttl = 5.minutes)
    logger.warn(s"Attempting to acquire lock....")
    lockService
      .withLock {
        logger.warn("Lock acquired. Starting queries....")
        for {
          duplicateQueryResult <- invitationsRepository.findDuplicateInvitations
          _ = logger.warn(s"Found: ${duplicateQueryResult.map(_.toDelete).sum} duplicate invitations.")
          indexQueryResult <- invitationsRepository.getIndexes
          _ = logger.warn(s"Indexes: $indexQueryResult")
        } yield ()
      }
      .map {
        case Some(_) => logger.warn(s"Checks complete")
        case None    => logger.warn(s"Failed to take lock $LOCK_ID")
      }
  } else logger.warn("Startup mongo query not enabled.")
}
