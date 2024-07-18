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

import java.util.UUID

import org.apache.pekko.Done
import com.google.inject.{Inject, Singleton}
import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.PlatformAnalyticsConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.http.HeaderCarrier

import scala.util.hashing.{MurmurHash3 => MH3}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class PlatformAnalyticsService @Inject() (connector: PlatformAnalyticsConnector, appConfig: AppConfig) {

  private val trackingId = appConfig.gaTrackingId
  private val clientTypeIndex = appConfig.gaClientTypeIndex
  private val invitationIdIndex = appConfig.gaInvitationIdIndex
  private val originIndex = appConfig.gaOriginIndex
  private val altItsaIndex = appConfig.gaAltItsaIndex

  val logger = Logger(getClass)

  def reportSingleEventAnalyticsRequest(i: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Done] = {
    logger.info(s"sending GA event for invitation: ${i.invitationId.value} with status: ${i.status} and origin: ${i.origin
        .getOrElse("origin_not_set")}")
    val maybeGAClientId: Option[String] = if (hc.sessionId.isDefined) None else Some(makeGAClientId)
    sendAnalyticsRequest(List(i), maybeGAClientId)
  }

  private def sendAnalyticsRequest(invitations: List[Invitation], clientId: Option[String])(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[Done] =
    connector.sendEvent(AnalyticsRequest(gaClientId = clientId, gaTrackingId = Some(trackingId), events = invitations.map(i => createEventFor(i))))

  private def createEventFor(i: Invitation): Event =
    i.status match {
      case Pending | PartialAuth => makeAuthRequestEvent("created", i)
      case Accepted              => makeAuthRequestEvent("accepted", i)
      case Rejected              => makeAuthRequestEvent("declined", i)
      case Expired               => makeAuthRequestEvent("expired", i)
      case Cancelled             => makeAuthRequestEvent("cancelled", i)
      case DeAuthorised          => makeAuthRequestEvent("deauthorised", i)
      case _: Unknown            => makeAuthRequestEvent("unknown", i)
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
      ) ++ i.altItsa.map(v => List(DimensionValue(altItsaIndex, v.toString))).getOrElse(List.empty)
    )

  // platform analytics will make a client ID from the session ID but when there is no session (eg for expired status) use this to make a client ID.
  private def makeGAClientId: String = {
    val uuid = UUID.randomUUID().toString
    MH3.stringHash(uuid, MH3.stringSeed).abs.toString match {
      case uuidHash =>
        "GA1.1." + (uuidHash + "000000000")
          .substring(0, 9) + "." + ("0000000000" + uuidHash).substring(uuidHash.length, 10 + uuidHash.length).reverse
    }
  }

}
