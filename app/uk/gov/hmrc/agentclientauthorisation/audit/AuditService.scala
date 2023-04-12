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

package uk.gov.hmrc.agentclientauthorisation.audit

import javax.inject.{Inject, Singleton}
import play.api.mvc.Request
import uk.gov.hmrc.agentclientauthorisation.audit.AgentClientInvitationEvent.AgentClientInvitationEvent
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object AgentClientInvitationEvent extends Enumeration { //partialAuth expired
  val AgentClientInvitationResponse, AgentClientRelationshipCreated, HmrcExpiredAgentServiceAuthorisation = Value
  type AgentClientInvitationEvent = Value
}

@Singleton
class AuditService @Inject()(val auditConnector: AuditConnector) {

  def sendAgentClientRelationshipCreated(invitationId: String, arn: Arn, clientId: ClientId, service: Service, isAltIsa: Boolean)(
    implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Unit] =
    auditEvent(
      AgentClientInvitationEvent.AgentClientRelationshipCreated,
      "agent-client-relationship-created",
      Seq(
        "invitationId"         -> invitationId,
        "agentReferenceNumber" -> arn.value,
        "clientIdType"         -> clientIdentifierType(clientId),
        "clientId"             -> clientId.value,
        "service"              -> service.id,
        "altITSASource"        -> isAltIsa
      )
    )

  def sendInvitationExpired(invitation: Invitation)(implicit hc: HeaderCarrier, request: Request[Any], ec: ExecutionContext): Future[Unit] =
    auditEvent(
      AgentClientInvitationEvent.AgentClientInvitationResponse,
      "Client responded to agent invitation",
      Seq(
        "invitationId"         -> invitation._id.toString,
        "agentReferenceNumber" -> invitation.arn.value,
        "clientIdType"         -> clientIdentifierType(invitation.clientId),
        "clientId"             -> invitation.clientId,
        "service"              -> invitation.service.id,
        "clientResponse"       -> "Expired"
      )
    )

  def sendHmrcExpiredAgentServiceAuthorisationAuditEvent(invitation: Invitation, deleteStatus: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] = {
    val details = Seq(
      "clientNINO"           -> invitation.suppliedClientId,
      "service"              -> invitation.service,
      "agentReferenceNumber" -> invitation.arn.value,
      "deleteStatus"         -> deleteStatus
    )
    val dataEvent = DataEvent(
      auditSource = "agent-client-authorisation",
      auditType = AgentClientInvitationEvent.HmrcExpiredAgentServiceAuthorisation.toString,
      tags = hc.toAuditTags(transactionName = "hmrc expired agent:service authorisation", path = ""),
      detail = hc.toAuditDetails(details.map(pair => pair._1 -> pair._2.toString): _*)
    )
    send(dataEvent)
  }

  private def clientIdentifierType(clientId: ClientId): String = clientId.underlying match {
    case _: Vrn               => "vrn"
    case _: Nino | _: MtdItId => "ni"
    case _: Utr               => "utr"
    case _: Urn               => "urn"
    case _: CgtRef            => "cgtRef"
    case _: PptRef            => "pptRef"
    case _                    => throw new IllegalStateException(s"Unsupported ClientIdType for ID: '$clientId'")
  }

  private[audit] def auditEvent(event: AgentClientInvitationEvent, transactionName: String, details: Seq[(String, Any)] = Seq.empty)(
    implicit hc: HeaderCarrier,
    request: Request[Any],
    ec: ExecutionContext): Future[Unit] =
    send(createEvent(event, transactionName, details: _*))

  private def createEvent(event: AgentClientInvitationEvent, transactionName: String, details: (String, Any)*)(
    implicit hc: HeaderCarrier,
    request: Request[Any]): DataEvent = {

    val detail = hc.toAuditDetails(details.map(pair => pair._1 -> pair._2.toString): _*)
    val tags = hc.toAuditTags(transactionName, request.path)
    DataEvent(auditSource = "agent-client-authorisation", auditType = event.toString, tags = tags, detail = detail)
  }

  private def send(events: DataEvent*)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }

}
