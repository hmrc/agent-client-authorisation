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

package uk.gov.hmrc.agentclientauthorisation.support

import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain._

trait StubUtils {
  me: StartAndStopWireMock =>

  class PreconditionBuilder {

    def agentAdmin(arn: String, agentCode: String): AgentAdmin = {
       AgentAdmin(arn, oid = "556737e15500005500eaf68e", agentCode)
    }

    def agentAdmin(arn: Arn, agentCode: AgentCode): AgentAdmin = {
      agentAdmin(arn.value, agentCode.value)
    }

    def client(oid: String = "556737e15500005500eaf68f", canonicalClientId: MtdItId = MtdItId("mtdItId1"), clientId: Nino = new Generator().nextNino ): Client = {
      Client(oid, clientId, canonicalClientId)
    }
  }

  def given() = {
    new PreconditionBuilder()
  }

  class BaseUser extends WiremockAware {
    override def wiremockBaseUrl: String = me.wiremockBaseUrl
  }

  case class AgentAdmin(
                         override val arn: String,
                         override val oid: String,
                         override val agentCode: String)
    extends BaseUser with AgentAuthStubs[AgentAdmin] {
  }

  case class Client(override val oid: String, override val clientId: Nino, override val canonicalClientId: MtdItId )
    extends BaseUser with ClientUserAuthStubs[Client] with RelationshipStubs[Client] with DesStubs[Client]
}
