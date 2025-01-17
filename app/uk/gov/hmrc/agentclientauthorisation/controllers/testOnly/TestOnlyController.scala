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
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext

@Singleton
class TestOnlyController @Inject() (
  agentReferenceRecordRepository: AgentReferenceRepository
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
}
