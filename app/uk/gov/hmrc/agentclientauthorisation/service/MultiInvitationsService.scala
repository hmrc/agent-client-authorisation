/*
 * Copyright 2018 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Named}
import org.apache.commons.lang3.RandomStringUtils
import org.joda.time.{DateTime, DateTimeZone}
import play.api.Logger
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, Monitor}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}

import scala.concurrent.{ExecutionContext, Future}

class MultiInvitationsService @Inject()(
  multiInvitationsRecordRepository: AgentReferenceRepository,
  auditService: AuditService,
  metrics: Metrics)
    extends Monitor {

  val codetable = "ABCDEFGHJKLMNOPRSTUWXYZ123456789"

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def create(arn: Arn, normalisedAgentName: String)(implicit ec: ExecutionContext): Future[String] = {
    val startDate = DateTime.now(DateTimeZone.UTC)
    val uid = RandomStringUtils.random(8, codetable)
    val multiInvitationRecord =
      AgentReferenceRecord(uid, arn, Seq(normalisedAgentName))
    monitor(s"Repository-Create-Multi-Invitation") {
      multiInvitationsRecordRepository.findByArn(arn).map {
        case Some(record) =>
        case None         =>
      }
      multiInvitationsRecordRepository
        .create(multiInvitationRecord)
        .map { result =>
          if (result == 1) {
            Logger.info(s"""Created multi invitation record with uid: $uid""")
            uid
          } else
            throw new Exception("Unexpected failure of multi-invitation db record creation")
        }
        .recoverWith {
          case e: DatabaseException if e.code.contains(11000) =>
            Logger.error(s"""Duplicate uid happened $uid, will try again""")
            create(arn, normalisedAgentName)
        }
    }
  }

  def findBy(uid: String)(implicit ec: ExecutionContext): Future[Option[AgentReferenceRecord]] =
    multiInvitationsRecordRepository.findBy(uid)
}
