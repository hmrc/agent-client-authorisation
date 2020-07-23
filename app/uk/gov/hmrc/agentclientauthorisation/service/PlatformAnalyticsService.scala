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

import akka.Done
import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.PlatformAnalyticsConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.clusterworkthrottling.{Rate, ThrottledWorkItemProcessor}
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class PlatformAnalyticsService @Inject()(
  repository: InvitationsRepository,
  connector: PlatformAnalyticsConnector,
  appConfig: AppConfig,
  actorSystem: ActorSystem) {

  private val interval = appConfig.invitationUpdateStatusInterval
  private val expiredWithin = interval.seconds.toMillis
  private val batchSize = appConfig.gaBatchSize
  private val clientId = appConfig.gaClientId
  private val trackingId = appConfig.gaTrackingId
  private val clientTypeIndex = appConfig.gaClientTypeIndex
  private val invitationIdIndex = appConfig.gaInvitationIdIndex
  private val originIndex = appConfig.gaOriginIndex
  private val throttleCallsPerSec = appConfig.gaThrottlingCallsPerSecond

  private val throttler =
    new ThrottledWorkItemProcessor("GAEventThrottler", actorSystem, Some(Rate.parse(s"$throttleCallsPerSec / second"))) {
      override def instanceCount: Int = 1
    }

  def reportExpiredInvitations()(implicit ec: ExecutionContext): Future[Unit] =
    repository
      .findInvitationsBy(status = Some(Expired))
      .map(_.filter(isExpiredWithIn))
      .map { expired =>
        Logger(getClass).info(s"sending GA events for expired invitations (total size: ${expired.size})")
        expired
          .grouped(batchSize)
          .foreach { batch =>
            throttleSendRequest(batch)
          }
      }

  private def isExpiredWithIn(invitation: Invitation): Boolean =
    invitation.mostRecentEvent().time.withDurationAdded(expiredWithin, 1).compareTo(DateTime.now) == 1

  private def throttleSendRequest(batch: List[Invitation])(implicit ec: ExecutionContext) = {
    Logger(getClass).info(s"sending throttling GA event for invitation batch with size: ${batch.size}")
    throttler.throttledStartingFrom(DateTime.now) {
      sendAnalyticsRequest(batch)
    }
  }

  def reportSingleEventAnalyticsRequest(i: Invitation)(implicit ec: ExecutionContext): Future[Done] = {
    Logger(getClass).info(s"sending GA event for invitation: ${i.invitationId.value} with status: ${i.status}")
    sendAnalyticsRequest(List(i))
  }

  private def sendAnalyticsRequest(invitations: List[Invitation])(implicit ec: ExecutionContext): Future[Done] = {
    implicit val hc: HeaderCarrier = HeaderCarrier(
      extraHeaders = Seq("AuthorisationRequestSendEvent-Batch-Size" -> s"${invitations.size}"))
    connector.sendEvent(
      AnalyticsRequest(
        gaClientId = clientId,
        gaTrackingId = trackingId,
        events = invitations.map(i => createEventFor(i))))
  }

  private def createEventFor(i: Invitation): Event =
    i.status match {
      case Pending    => makeAuthRequestEvent("authorisation request", "created", i)
      case Accepted   => makeAuthRequestEvent("authorisation request", "accepted", i)
      case Rejected   => makeAuthRequestEvent("authorisation request", "declined", i)
      case Expired    => makeAuthRequestEvent("authorisation request", "expired", i)
      case Cancelled  => makeAuthRequestEvent("authorisation request", "cancelled", i)
      case s: Unknown => makeAuthRequestEvent("authorisation request", "unknown", i)
    }

  private def makeAuthRequestEvent(category: String, action: String, i: Invitation): Event =
    Event(
      category = category,
      action = action,
      label = i.service.id.toLowerCase,
      dimensions = List(
        DimensionValue(clientTypeIndex, i.clientType.getOrElse("unknown")),
        DimensionValue(invitationIdIndex, i.invitationId.value),
        DimensionValue(originIndex, i.origin.getOrElse("unknown"))
      )
    )

}
