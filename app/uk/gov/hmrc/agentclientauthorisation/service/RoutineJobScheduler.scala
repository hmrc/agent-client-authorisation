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

package uk.gov.hmrc.agentclientauthorisation.service

import org.apache.pekko.actor.{Actor, ActorSystem, Props}
import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.repository.{RemovePersonalInfo, ScheduleRecord, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.util.toFuture

import java.time.{Instant, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class RoutineJobScheduler @Inject() (
  scheduleRepository: ScheduleRepository,
  invitationsService: InvitationsService,
  actorSystem: ActorSystem,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  val enabled = appConfig.removePersonalInfoSchedulerEnabled
  val interval = appConfig.removePersonalInfoScheduleInterval
  val altItsaExpiryEnable = appConfig.altItsaExpiryEnable

  if (enabled) {
    logger.info(s"routine job scheduler is enabled.")
    val removePersonalInfoActor = actorSystem.actorOf(Props {
      new RoutineScheduledJobActor(scheduleRepository, invitationsService, interval, altItsaExpiryEnable)
    })
    actorSystem.scheduler.scheduleOnce(2.seconds, removePersonalInfoActor, "<Initial RoutineJobSchedulerActor message>")
  } else {
    logger.warn(s"routine job scheduler is not enabled.")
  }
}

class RoutineScheduledJobActor(
  scheduleRepository: ScheduleRepository,
  invitationsService: InvitationsService,
  repeatInterval: Int,
  altItsaExpiryEnable: Boolean
)(implicit ec: ExecutionContext)
    extends Actor with Logging {

  def receive = { case uid: String =>
    logger.info("RoutineJobSchedulerActor Received message: " + uid)
    logger.info(s"RoutineJobSchedulerActor: alt-itsa-expiry-enable is set to $altItsaExpiryEnable")
    try
      scheduleRepository.read(RemovePersonalInfo).flatMap { case ScheduleRecord(recordUid, runAt, _) =>
        val now = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime
        if (uid == recordUid) {
          val newUid = UUID.randomUUID().toString
          val nextRunAt = (if (runAt.isBefore(now)) now else runAt).plusSeconds(repeatInterval + Random.nextInt(Math.min(60, repeatInterval)))
          val delay = nextRunAt.toEpochSecond(ZoneOffset.UTC) - now.toEpochSecond(ZoneOffset.UTC)
          scheduleRepository
            .write(newUid, nextRunAt, RemovePersonalInfo)
            .map { _ =>
              context.system.scheduler.scheduleOnce(delay.seconds, self, newUid)
              logger.info(s"Starting routine scheduled job, next job is scheduled at $nextRunAt")
              logger.info(s"alt-itsa-expiry-enable is set to $altItsaExpiryEnable")
              logger.info(s"Out-of-date alt-ITSA invitations (if found) ${if (altItsaExpiryEnable) "WILL" else "will NOT"} be set to expired.")
              for {
                _ <- invitationsService.prepareAndSendWarningEmail()
                _ <- invitationsService.removePersonalDetails()
                _ <- if (altItsaExpiryEnable) invitationsService.cancelOldAltItsaInvitations() else Future.successful(())
              } yield (logger info "routine scheduled job completed")
            }
        } else { // There are several instances of this service running
          val dateTime = if (runAt.isBefore(now)) now else runAt
          val intervalSeconds = dateTime.toEpochSecond(ZoneOffset.UTC) - now.toEpochSecond(ZoneOffset.UTC)
          val delay = (intervalSeconds + Random.nextInt(Math.min(60, repeatInterval))).seconds
          context.system.scheduler.scheduleOnce(delay, self, recordUid)
          toFuture(())
        }
      }
    catch {
      case ex: Exception => logger.error(s"Exception in RoutineJobSchedulerActor: ${ex.getMessage}", ex)
    }
    ()
  }
}
