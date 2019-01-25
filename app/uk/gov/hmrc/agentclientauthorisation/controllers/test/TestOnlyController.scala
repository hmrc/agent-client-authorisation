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

package uk.gov.hmrc.agentclientauthorisation.controllers.test
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Provider}
import play.api.Logger
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

case class TestOnlyAgentRefRecord(uid: String, arn: Arn, normalisedAgentName: String)

object TestOnlyAgentRefRecord {

  def apply(arg: AgentReferenceRecord): TestOnlyAgentRefRecord =
    TestOnlyAgentRefRecord(arg.uid, arg.arn, arg.normalisedAgentNames.last)

  implicit val format: OFormat[TestOnlyAgentRefRecord] = Json.format[TestOnlyAgentRefRecord]
}

class TestOnlyController @Inject()(agentReferenceRecordRepository: AgentReferenceRepository)(
  implicit
  metrics: Metrics,
  ecp: Provider[ExecutionContextExecutor])
    extends BaseController {

  implicit val ec: ExecutionContext = ecp.get

  def getAgentReferenceRecordByArn(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    agentReferenceRecordRepository
      .findByArn(arn)
      .map {
        case Some(multiInvitationRecord) =>
          Ok(Json.toJson(TestOnlyAgentRefRecord(multiInvitationRecord)))
        case None =>
          Logger(getClass).warn(s"Agent Reference Record not found for: ${arn.value}")
          NotFound
      }
      .recoverWith {
        case e =>
          Future failed (throw new Exception(
            s"Something has gone wrong for: ${arn.value}. Error found: ${e.getMessage}"))
      }
  }
}
