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
import javax.inject.Inject
import org.apache.commons.lang3.RandomStringUtils
import play.api.Logger
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AgentServicesAccountConnector
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, MongoAgentReferenceRepository, Monitor}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class AgentLinkService @Inject()(
  multiInvitationsRecordRepository: AgentReferenceRepository,
  agentServicesAccountConnector: AgentServicesAccountConnector,
  auditService: AuditService,
  metrics: Metrics)
    extends Monitor {

  val codetable = "ABCDEFGHJKLMNOPRSTUWXYZ123456789"

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getAgentLink(arn: Arn, clientType: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[String] =
    for {
      normalisedAgentName <- agentServicesAccountConnector.getAgencyNameAgent.map(name => normaliseAgentName(name.get))
      recordOpt           <- multiInvitationsRecordRepository.findByArn(arn)
      record: AgentReferenceRecord <- recordOpt match {

                                       case Some(record) =>
                                         if (record.normalisedAgentNames.contains(normalisedAgentName))
                                           Future.successful(record)
                                         else
                                           multiInvitationsRecordRepository
                                             .updateAgentName(record.uid, normalisedAgentName)
                                             .map(_ => record)
                                       case None => create(arn, normalisedAgentName, clientType)
                                     }
    } yield s"/invitations/$clientType/${record.uid}/$normalisedAgentName"

  private def create(arn: Arn, normalisedAgentName: String, clientType: String)(
    implicit ec: ExecutionContext): Future[AgentReferenceRecord] = {
    val uid = RandomStringUtils.random(8, codetable)
    val newRecord =
      AgentReferenceRecord(uid, arn, Seq(normalisedAgentName))
    multiInvitationsRecordRepository
      .create(newRecord)
      .map { result =>
        if (result == 1) {
          Logger.info(s"""Created multi invitation record with uid: $uid""")
          newRecord
        } else
          throw new Exception("Unexpected failure of multi-invitation db record creation")
      }
      .recoverWith {
        case e: DatabaseException if e.code.contains(11000) =>
          Logger.error(s"""Duplicate uid happened $uid, will try again""")
          create(arn, normalisedAgentName, clientType)
      }

  }

  private def normaliseAgentName(agentName: String) =
    agentName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9-]", "")
}
