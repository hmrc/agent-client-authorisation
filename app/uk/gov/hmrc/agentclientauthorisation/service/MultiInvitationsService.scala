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
import org.joda.time.{DateTime, DateTimeZone}
import uk.gov.hmrc.agentclientauthorisation.repository.{Monitor, MultiInvitationRecord, MultiInvitationRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.Duration

class MultiInvitationsService @Inject()(
  multiInvitationsRecordRepository: MultiInvitationRepository,
  auditService: AuditService,
  @Named("invitation.expiryDuration")
  invitationExpiryDurationValue: String,
  metrics: Metrics)
    extends Monitor {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private val invitationExpiryDuration = Duration(invitationExpiryDurationValue.replace('_', ' '))

  def create(arn: Arn, uid: String, invitationIds: Seq[InvitationId], clientType: String)(
    implicit ec: ExecutionContext): Future[Int] = {
    val startDate = DateTime.now(DateTimeZone.UTC)
    val expiryDate = startDate.plus(invitationExpiryDuration.toMillis)
    val multiInvitationRecord = MultiInvitationRecord(uid, arn, invitationIds, clientType, startDate, expiryDate)
    monitor(s"Repository-Create-Multi-Invitation") {
      multiInvitationsRecordRepository.create(multiInvitationRecord).map { result =>
        Logger info s"""Created multi invitation record with uid: $uid"""
        result
      }
    }
  }
}
