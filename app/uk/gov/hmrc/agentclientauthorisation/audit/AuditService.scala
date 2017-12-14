/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationStatus, Service}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future
import scala.util.Try

object AgentClientInvitationEvent extends Enumeration {
  val AgentClientInvitationResponse, AgentClientRelationshipCreated = Value
  type AgentClientInvitationEvent = Value
}

@Singleton
class AuditService @Inject()(val auditConnector: AuditConnector) {

  def sendAgentClientRelationshipCreated(invitationId: String, arn: Arn, taxId: TaxIdentifier, service: Service)
                                 (implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    auditEvent(AgentClientInvitationEvent.AgentClientRelationshipCreated, "agent-client-relationship-created",
      Seq(
        "invitationId" -> invitationId,
        "agentReferenceNumber" -> arn.value,
        "regimeId" -> taxId.value,
        "regime" -> service.id
      ))
  }

  def sendInvitationExpired(invitation: Invitation)(implicit hc: HeaderCarrier, request: Request[Any]): Unit = {
    auditEvent(AgentClientInvitationEvent.AgentClientInvitationResponse, "Client responded to agent invitation",
      Seq(
        "invitationId" -> invitation.id.stringify,
        "agentReferenceNumber" -> invitation.arn,
        "regimeId" -> invitation.clientId,
        "regime" -> invitation.service.id
      ))
  }

  private[audit] def auditEvent(event: AgentClientInvitationEvent, transactionName: String, details: Seq[(String, Any)] = Seq.empty)(implicit hc: HeaderCarrier, request: Request[Any]): Future[Unit] = {
    send(createEvent(event, transactionName, details: _*))
  }

  private def createEvent(event: AgentClientInvitationEvent, transactionName: String, details: (String, Any)*)(implicit hc: HeaderCarrier, request: Request[Any]): DataEvent = {

    val detail = hc.toAuditDetails(details.map(pair => pair._1 -> pair._2.toString): _*)
    val tags = hc.toAuditTags(transactionName, request.path)
    DataEvent(
      auditSource = "agent-client-authorisation",
      auditType = event.toString,
      tags = tags,
      detail = detail)
  }

  private def send(events: DataEvent*)(implicit hc: HeaderCarrier): Future[Unit] = {
    Future {
      events.foreach { event =>
        Try(auditConnector.sendEvent(event))
      }
    }
  }

}
