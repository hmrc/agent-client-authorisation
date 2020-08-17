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

import akka.Done
import akka.actor.ActorSystem
import akka.stream.Materializer
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.PlatformAnalyticsConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.http.HeaderCarrier

import scala.util.hashing.{MurmurHash3 => MH3}
import scala.collection.Seq
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

@Singleton
class PlatformAnalyticsService @Inject()(
  repository: InvitationsRepository,
  connector: PlatformAnalyticsConnector,
  appConfig: AppConfig,
  actorSystem: ActorSystem)(implicit mat: Materializer) {

  private val interval = appConfig.invitationUpdateStatusInterval
  private val expiredWithin = interval.seconds.toMillis
  private val batchSize = appConfig.gaBatchSize
  private val trackingId = appConfig.gaTrackingId
  private val clientTypeIndex = appConfig.gaClientTypeIndex
  private val invitationIdIndex = appConfig.gaInvitationIdIndex
  private val originIndex = appConfig.gaOriginIndex

  val logger = Logger(getClass)

  def reportExpiredInvitations()(implicit ec: ExecutionContext): Future[Unit] =
    repository
      .getExpiredInvitationsForGA(expiredWithin)
      .map { expired =>
        logger.info(s"sending GA events for expired invitations (total size: ${expired.size})")
        expired
          .grouped(batchSize)
          .foreach { batch =>
            sendAnalyticsRequest(batch, Some(makeGAClientId))(
              HeaderCarrier(extraHeaders = Seq("AuthorisationRequestSendEvent-Batch-Size" -> s"${batch.size}")),
              ec)
          }
      }

  def reportSingleEventAnalyticsRequest(
    i: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    logger.info(s"sending GA event for invitation: ${i.invitationId.value} with status: ${i.status}")
    sendAnalyticsRequest(List(i), None)
  }

  private def sendAnalyticsRequest(invitations: List[Invitation], clientId: Option[String])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Done] =
    connector.sendEvent(
      AnalyticsRequest(
        gaClientId = clientId,
        gaTrackingId = Some(trackingId),
        events = invitations.map(i => createEventFor(i))))

  private def createEventFor(i: Invitation): Event =
    i.status match {
      case Pending    => makeAuthRequestEvent("created", i)
      case Accepted   => makeAuthRequestEvent("accepted", i)
      case Rejected   => makeAuthRequestEvent("declined", i)
      case Expired    => makeAuthRequestEvent("expired", i)
      case Cancelled  => makeAuthRequestEvent("cancelled", i)
      case s: Unknown => makeAuthRequestEvent("unknown", i)
    }

  private def makeAuthRequestEvent(action: String, i: Invitation): Event =
    Event(
      category = "authorisation request",
      action = action,
      label = i.service.id.toLowerCase,
      dimensions = List(
        DimensionValue(clientTypeIndex, i.clientType.getOrElse("unknown")),
        DimensionValue(invitationIdIndex, i.invitationId.value),
        DimensionValue(originIndex, i.origin.getOrElse("unknown"))
      ),
      userId = None
    )

  // platform analytics will make a client ID from the session ID but when there is no session make a clientId from a UUID
  private def makeGAClientId: String = {
    val uuid = UUID.randomUUID().toString
    MH3.stringHash(uuid, MH3.stringSeed).abs.toString match {
      case uuidHash =>
        "GA.1." + (uuidHash + "000000000")
          .substring(0, 9) + "." + ("0000000000" + uuidHash).substring(uuidHash.length, 10 + uuidHash.length).reverse
    }
  }

}
