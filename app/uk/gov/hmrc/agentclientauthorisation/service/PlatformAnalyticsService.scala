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
import com.google.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.PlatformAnalyticsConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformAnalyticsService @Inject()(
  repository: InvitationsRepository,
  connector: PlatformAnalyticsConnector,
  appConfig: AppConfig) {

  private val interval = appConfig.invitationUpdateStatusInterval
  private val batchSize = appConfig.gaBatchSize
  private val clientId = appConfig.gaClientId
  private val trackingId = appConfig.gaTrackingId
  private val clientTypeIndex = appConfig.gaClientTypeIndex
  private val invitationIdIndex = appConfig.gaInvitationIdIndex
  private val originIndex = appConfig.gaOriginIndex

  def reportExpiredInvitations()(implicit ec: ExecutionContext): Future[Unit] = {
    val expiredWithin = interval.seconds.toMillis
    repository
      .findInvitationsBy(status = Some(Expired))
      .map(_.filter(_.mostRecentEvent().time.withDurationAdded(expiredWithin, 1).compareTo(DateTime.now) == 1))
      .map {
        _.grouped(batchSize)
          .foreach(group => sendAnalyticsRequest(group))
      }
  }

  def reportAuthorisationRequestCreated(i: Invitation)(implicit ec: ExecutionContext): Future[Done] = {
    Logger(getClass).info(s"sending analytics event for authorisation request created id: ${i.id.stringify}")
    sendAnalyticsRequest(List(i))
  }

  private def sendAnalyticsRequest(invitations: List[Invitation])(implicit ec: ExecutionContext): Future[Done] = {
    implicit val hc: HeaderCarrier = HeaderCarrier(
      extraHeaders = Seq("Expired-Invitation-Batch-Size" -> s"${invitations.size}"))
    connector.sendEvent(
      AnalyticsRequest(
        gaClientId = clientId,
        gaTrackingId = trackingId,
        events = invitations.map(i => createEventFor(i))))
  }

  private def createEventFor(i: Invitation): Event =
    i.status match {
      case Pending    => makeAuthRequestEvent("create", i)
      case Accepted   => makeAuthRequestEvent("accept", i)
      case Rejected   => makeAuthRequestEvent("decline", i)
      case Expired    => makeAuthRequestEvent("expired", i)
      case Cancelled  => makeAuthRequestEvent("cancel", i)
      case s: Unknown => makeAuthRequestEvent("unknown", i)
    }

  /* google-analytics assigned index numbers for custom dimensions (TSR-18085):
   Agent Client Type *index 7*
   Agent invitation ID *index 8*
   Agent Auth Request Origin *index 9*
   */

  private def makeAuthRequestEvent(action: String, i: Invitation): Event =
    Event(
      category = "authorisation request",
      action = s"$action",
      label = s"${i.service.id.toLowerCase}",
      dimensions = List(
        DimensionValue(clientTypeIndex, i.clientType.getOrElse("unknown")),
        DimensionValue(invitationIdIndex, i.invitationId.value),
        DimensionValue(originIndex, i.origin.getOrElse("unknown"))
      )
    )

}
