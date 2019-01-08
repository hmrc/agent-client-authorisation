/*
 * Copyright 2019 HM Revenue & Customs
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

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import javax.inject.{Inject, Named, Singleton}
import org.joda.time.{DateTime, Interval, LocalDate, PeriodType}
import play.api.Logger
import play.api.libs.concurrent.ExecutionContextProvider
import uk.gov.hmrc.agentclientauthorisation.model.{Expired, Pending}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, ScheduleRecord, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.util.toFuture

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Random

@Singleton
class InvitationsStatusUpdateScheduler @Inject()(
  scheduleRepository: ScheduleRepository,
  invitationsRepository: InvitationsRepository,
  actorSystem: ActorSystem,
  @Named("invitation-status-update-scheduler.interval") interval: Int,
  @Named("invitation-status-update-scheduler.enabled") enabled: Boolean,
  executionContextProvider: ExecutionContextProvider
) {

  implicit val ec: ExecutionContext = executionContextProvider.get()

  if (enabled) {
    Logger(getClass).info("invitation status update scheduler is enabled.")
    val taskActor: ActorRef = actorSystem.actorOf(Props {
      new TaskActor(
        scheduleRepository,
        invitationsRepository,
        executionContextProvider,
        interval,
        updateInvitations()
      )
    })
    actorSystem.scheduler.scheduleOnce(
      5.seconds,
      taskActor,
      "<start invitation status update scheduler>"
    )
  } else
    Logger(getClass).warn("invitation status update scheduler is not enabled.")

  private def updateInvitations(): Future[Unit] =
    invitationsRepository
      .findInvitationsBy(status = Some(Pending))
      .map { invitations =>
        invitations.foreach { invitation =>
          if (invitation.expiryDate.isBefore(LocalDate.now())) {
            invitationsRepository.update(invitation, Expired, DateTime.now())
          }
        }
      }
}

class TaskActor(
  scheduleRepository: ScheduleRepository,
  invitationsRepository: InvitationsRepository,
  executionContextProvider: ExecutionContextProvider,
  repeatInterval: Int,
  updateInvitations: => Future[Unit])
    extends Actor {

  implicit val ec: ExecutionContext = executionContextProvider.get()

  def receive = {
    case uid: String =>
      scheduleRepository.read.flatMap {
        case ScheduleRecord(recordUid, runAt) =>
          if (uid == recordUid) {
            val newUid = UUID.randomUUID().toString
            val nextRunAt = runAt.plusSeconds(repeatInterval)
            scheduleRepository
              .write(newUid, nextRunAt)
              .map(_ => {
                context.system.scheduler
                  .scheduleOnce(delay(nextRunAt), self, newUid)
                Logger(getClass)
                  .info(s"Starting update invitation status job, next job is scheduled at $nextRunAt")
                updateInvitations
              })
          } else {
            context.system.scheduler.scheduleOnce(delay(runAt), self, recordUid)
            toFuture(())
          }
      }
  }

  private def delay(runAt: DateTime) = {
    val now = DateTime.now()
    (if (runAt.isBefore(now)) repeatInterval
     else
       new Interval(DateTime.now(), runAt)
         .toPeriod(PeriodType.seconds())
         .getValue(0) + Random
         .nextInt(Math.min(60, repeatInterval))).seconds
  }
}
