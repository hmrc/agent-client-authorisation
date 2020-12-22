/*
 * Copyright 2020 HM Revenue & Customs
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

import java.util.UUID

import akka.actor.{Actor, ActorSystem, Props}
import javax.inject.{Inject, Singleton}
import org.joda.time.{DateTime, Interval, PeriodType}
import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.repository.{ScheduleRecord, ScheduleRepository, SchedulerType}
import uk.gov.hmrc.agentclientauthorisation.util.toFuture

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random

@Singleton
class RemovePersonalInfoScheduler @Inject()(
  scheduleRepository: ScheduleRepository,
  invitationsService: InvitationsService,
  actorSystem: ActorSystem,
  appConfig: AppConfig
)(implicit ec: ExecutionContext)
    extends Logging {

  val interval = appConfig.removePersonalInfoScheduleInterval

  val removePersonalInfoActor = actorSystem.actorOf(Props { new RemovePersonalInfoActor(scheduleRepository, invitationsService, interval) })
  actorSystem.scheduler.scheduleOnce(2.seconds, removePersonalInfoActor, "<Initial RemovePersonalInfoActor message>")

}

class RemovePersonalInfoActor(scheduleRepository: ScheduleRepository, invitationsService: InvitationsService, repeatInterval: Int)(
  implicit ec: ExecutionContext)
    extends Actor with Logging {

  def receive = {
    case uid: String =>
      logger.info("RemovePersonalInfoActor Received message: " + uid)
      try {
        scheduleRepository.read(SchedulerType.RemovePersonalInfo).flatMap {
          case ScheduleRecord(recordUid, runAt, _) =>
            val now = DateTime.now()
            if (uid == recordUid) {
              val newUid = UUID.randomUUID().toString
              val nextRunAt = (if (runAt.isBefore(now)) now else runAt).plusSeconds(repeatInterval + Random.nextInt(Math.min(60, repeatInterval)))
              val delay = new Interval(now, nextRunAt).toPeriod(PeriodType.seconds()).getValue(0).seconds
              scheduleRepository
                .write(newUid, nextRunAt, SchedulerType.RemovePersonalInfo)
                .map { _ =>
                  context.system.scheduler.scheduleOnce(delay, self, newUid)
                  logger.info(s"Starting to remove personal info job, next job is scheduled at $nextRunAt")
                  invitationsService.removePersonalDetails()
                }
            } else { //There are several instances of this service running
              val dateTime = if (runAt.isBefore(now)) now else runAt
              val intervalSeconds = new Interval(now, dateTime).toPeriod(PeriodType.seconds()).getValue(0)
              val delay = (intervalSeconds + Random.nextInt(Math.min(60, repeatInterval))).seconds
              context.system.scheduler.scheduleOnce(delay, self, recordUid)
              toFuture(())
            }
        }
      } catch {
        case ex: Exception => logger.error(s"Exception in RemovePersonalInfoActor: ${ex.getMessage}", ex)
      }
      ()
  }
}
