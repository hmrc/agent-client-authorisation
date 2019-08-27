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
  agentReferenceRecordRepository: AgentReferenceRepository,
  agentServicesAccountConnector: AgentServicesAccountConnector,
  auditService: AuditService,
  metrics: Metrics)
    extends Monitor {

  val codetable = "ABCDEFGHJKLMNOPRSTUWXYZ123456789"

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  def getInvitationUrl(arn: Arn, clientType: String)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[String] =
    for {
      normalisedAgentName <- agentServicesAccountConnector.getAgencyNameAgent.map(name => normaliseAgentName(name.get))
      record              <- fetchOrCreateRecord(arn, normalisedAgentName)
    } yield s"/invitations/$clientType/${record.uid}/$normalisedAgentName"

  def getRecord(arn: Arn)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[AgentReferenceRecord] =
    for {
      normalisedName <- agentServicesAccountConnector
                         .getAgencyNameViaClient(arn)
                         .map(name => normaliseAgentName(name.get))
      record <- fetchOrCreateRecord(arn, normalisedName)
    } yield record

  def fetchOrCreateRecord(arn: Arn, normalisedAgentName: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[AgentReferenceRecord] =
    for {
      recordOpt <- agentReferenceRecordRepository.findByArn(arn)
      record: AgentReferenceRecord <- recordOpt match {
                                       case Some(record) =>
                                         if (record.normalisedAgentNames.contains(normalisedAgentName))
                                           Future.successful(record)
                                         else
                                           agentReferenceRecordRepository
                                             .updateAgentName(record.uid, normalisedAgentName)
                                             .map(
                                               _ =>
                                                 record.copy(normalisedAgentNames = record.normalisedAgentNames ++ Seq(
                                                   normalisedAgentName)))
                                       case None =>
                                         create(arn, normalisedAgentName)
                                     }
    } yield record

  private def create(arn: Arn, normalisedAgentName: String, counter: Int = 1)(
    implicit ec: ExecutionContext): Future[AgentReferenceRecord] = {
    val uid = RandomStringUtils.random(8, codetable)
    val newRecord =
      AgentReferenceRecord(uid, arn, Seq(normalisedAgentName))
    agentReferenceRecordRepository
      .create(newRecord)
      .map { result =>
        if (result == 1) {
          Logger.info(s"""Created multi invitation record with uid: $uid""")
          newRecord
        } else
          throw new Exception("Unexpected failure of agent-reference db record creation")
      }
      .recoverWith {
        case e: DatabaseException if e.code.contains(11000) =>
          if (e.getMessage().contains("arn"))
            agentReferenceRecordRepository
              .findByArn(arn)
              .map(_.getOrElse(
                throw new IllegalStateException(s"Failure creating agent reference record for ${arn.value}")))
          else if (counter <= 3) {
            Logger.error(s"""Duplicate uid happened $uid, will try again""")
            create(arn, normalisedAgentName, counter + 1)
          } else
            Future.failed(e)
      }

  }

  private def normaliseAgentName(agentName: String) =
    agentName.toLowerCase().replaceAll("\\s+", "-").replaceAll("[^A-Za-z0-9-]", "")
}
