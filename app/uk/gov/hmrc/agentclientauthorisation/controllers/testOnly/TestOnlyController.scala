/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers.testOnly

import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentclientauthorisation.model.{DetailsForEmail, Invitation, PartialAuth}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, NinoType, Service}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.domain.{Generator, Nino}

import java.time.{LocalDate, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject() (
  agentReferenceRecordRepository: AgentReferenceRepository,
  invitationsRepository: InvitationsRepository
)(implicit cc: ControllerComponents, ec: ExecutionContext)
    extends BackendController(cc) {

  private def dropAgentReferenceCollection = agentReferenceRecordRepository.testOnlyDropAgentReferenceCollection()
  def createAgentReferenceTestData(numberOfRecords: Int): Action[AnyContent] = Action.async {
    dropAgentReferenceCollection.map { _ =>
      for (x <- 1 to numberOfRecords) yield {
        val uid = ("0000000" + x).takeRight(7)
        val arn = "AARN" + uid
        val normalisedAgentNames = Seq("agent-name-1", "agent-name-2")
        agentReferenceRecordRepository.create(AgentReferenceRecord(uid, Arn(arn), normalisedAgentNames))
      }
      Ok(s"Created $numberOfRecords agent reference records")
    }
  }

  def createPartialAuthInvitations(numberOfInvitations: Int): Action[AnyContent] = Action {
    for (x <- 1 to numberOfInvitations) yield {
      val clientId = ClientIdentifier(new Generator().nextNino.value, NinoType.id)
      invitationsRepository
        .create(
          Arn("XARN" + ("0000000" + x).takeRight(7)),
          clientType = Some("personal"),
          service = MtdIt,
          clientId = clientId,
          suppliedClientId = clientId,
          detailsForEmail = Some(DetailsForEmail(agencyName = "", agencyEmail = "", clientName = "clientName")),
          origin = None
        )
        .map(invitation => invitationsRepository.update(invitation, status = PartialAuth, updateDate = LocalDateTime.now()))
    }
    Ok(s"Created $numberOfInvitations partialAuth invitations")
  }
}
