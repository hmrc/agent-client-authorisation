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

package uk.gov.hmrc.agentclientauthorisation.service

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationExpired, ScheduleRecord, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.util.toFuture

import java.time.{Instant, ZoneOffset}
import java.util.UUID
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

@Singleton
class InvitationsStatusUpdateScheduler @Inject()(
  scheduleRepository: ScheduleRepository,
  invitationsService: InvitationsService,
  analyticsService: PlatformAnalyticsService,
  actorSystem: ActorSystem,
  appConfig: AppConfig
)(implicit ec: ExecutionContext) {

  val interval = appConfig.invitationUpdateStatusInterval
  val enabled = appConfig.invitationStatusUpdateEnabled

  if (enabled) {
    Logger(getClass).info("invitation status update scheduler is enabled.")
    val taskActor: ActorRef = actorSystem.actorOf(Props {
      new TaskActor(
        scheduleRepository,
        invitationsService,
        analyticsService,
        interval
      )
    })
    actorSystem.scheduler.scheduleOnce(
      interval.seconds,
      taskActor,
      "<start invitation status update scheduler>"
    )
  } else {
    Logger(getClass).warn("invitation status update scheduler is not enabled.")
  }

}

class TaskActor(
  scheduleRepository: ScheduleRepository,
  invitationsService: InvitationsService,
  analyticsService: PlatformAnalyticsService,
  repeatInterval: Int)(implicit ec: ExecutionContext)
    extends Actor {

  def receive = {
    case uid: String =>
      scheduleRepository.read(InvitationExpired).flatMap {
        case ScheduleRecord(recordUid, runAt, _) =>
          val now = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime
          if (uid == recordUid) {
            val newUid = UUID.randomUUID().toString
            val nextRunAt = (if (runAt.isBefore(now)) now else runAt)
              .plusSeconds(repeatInterval + Random.nextInt(Math.min(60, repeatInterval)))
            val delay = nextRunAt.toEpochSecond(ZoneOffset.UTC) - now.toEpochSecond(ZoneOffset.UTC)
            scheduleRepository
              .write(newUid, nextRunAt, InvitationExpired)
              .map(_ => {
                context.system.scheduler.scheduleOnce(delay.seconds, self, newUid)
                Logger(getClass).info(s"Starting update invitation status job, next job is scheduled at $nextRunAt")
                invitationsService
                  .findAndUpdateExpiredInvitations()
                  .map(_ => analyticsService.reportExpiredInvitations())
              })
          } else {
            val localDateTime = if (runAt.isBefore(now)) now else runAt
            val intervalSeconds = localDateTime.toEpochSecond(ZoneOffset.UTC) - now.toEpochSecond(ZoneOffset.UTC)
            val delay = (intervalSeconds + Random.nextInt(Math.min(60, repeatInterval))).seconds
            context.system.scheduler.scheduleOnce(delay, self, recordUid)
            toFuture(())
          }
      }
      ()
  }
}
