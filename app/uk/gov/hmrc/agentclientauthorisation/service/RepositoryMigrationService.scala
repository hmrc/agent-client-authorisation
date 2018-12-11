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

package uk.gov.hmrc.agentclientauthorisation.service

import javax.inject.{Inject, _}
import play.api.Logger
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, MigrationsRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class RepositoryMigrationService @Inject()(
  invitationsRepository: InvitationsRepository,
  migrationsRepository: MigrationsRepository,
  ecp: Provider[ExecutionContext]) {

  implicit val ec: ExecutionContext = ecp.get

  val migrationId = "re-index all invitations"

  def run(): Future[Unit] = {

    Thread.sleep(Random.nextInt(15000) + 1000)

    (for {
      _ <- migrationsRepository.tryLock(migrationId)
      _ = Logger(getClass).info("Starting invitations repository migration ...")
      _ <- migrate(migrationId)
      _ <- migrationsRepository.markDone(migrationId)
    } yield {
      Logger(getClass).info("Migration completed.")
    }).recover {
      case e: DatabaseException if e.code.contains(11000) => Logger(getClass).error(s"Migration already done.")
      case e                                              => Logger(getClass).error("Migration has failed", e)
    }
  }

  def migrate: String => Future[Unit] = {
    case "re-index all invitations" => invitationsRepository.refreshAllInvitations
    case id                         => Future.failed(new Exception(s"Unknown migration id = $id"))
  }

  run()

}
