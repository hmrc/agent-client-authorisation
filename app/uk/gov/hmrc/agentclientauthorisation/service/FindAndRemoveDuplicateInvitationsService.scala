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
@Singleton
class FindAndRemoveDuplicateInvitationsService @Inject()(
  invitationsRepository: InvitationsRepository,
  mongoLockRepository: MongoLockRepository,
  appConfig: AppConfig)
    extends Logging {

  private val LOCK_ID = "startup-lock"

  private val enabled = appConfig.startupMongoQueryEnabled

  if (enabled) {
    val lockService: LockService = LockService(mongoLockRepository, lockId = LOCK_ID, ttl = 1.minute)
    logger.warn(s"Attempting to acquire lock....")
    lockService
      .withLock {
        logger.warn("Lock acquired. Starting query....")
        for {
          queryResult <- invitationsRepository.findDuplicateInvitations
          _ = logger.warn(s"Found: ${queryResult.map(_.toDelete).sum} duplicate invitations. Now deleting the duplicates...")
          objectIds <- Future.sequence(queryResult.map(invitationsRepository.getObjectIdsForInvitations))
          _         <- invitationsRepository.deleteMany(objectIds.flatten)
          check     <- invitationsRepository.findDuplicateInvitations
        } yield logger.warn(s"Query complete. Check: ${check.map(_.toDelete).sum} duplicates remain.")
      }
      .map {
        case Some(_) => logger.warn(s"$LOCK_ID has been released.")
        case None    => logger.warn(s"Failed to take lock $LOCK_ID")
      }
  } else logger.warn("Startup mongo query not enabled.")
}
