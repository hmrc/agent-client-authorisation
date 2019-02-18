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

package uk.gov.hmrc.agentclientauthorisation.support

import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.nino1
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain._

trait StubUtils {
  me: StartAndStopWireMock =>

  class PreconditionBuilder {

    def agentAdmin(arn: Arn): AgentAdmin =
      AgentAdmin(arn.value)

    def agentAdmin(arn: String, agentCode: String): AgentAdmin =
      AgentAdmin(arn)

    def agentAdmin(arn: Arn, agentCode: AgentCode): AgentAdmin =
      agentAdmin(arn.value, agentCode.value)

    def client(canonicalClientId: TaxIdentifier = MtdItId("mtdItId1"), clientId: ClientId = nino1): Client =
      Client(clientId, canonicalClientId)
  }

  def given() =
    new PreconditionBuilder()

  class BaseUser extends WiremockAware {
    override def wiremockBaseUrl: String = me.wiremockBaseUrl
  }

  case class AgentAdmin(val arn: String) extends BaseUser with AgentAuthStubs {}

  case class Client(val clientId: ClientId, val canonicalClientId: TaxIdentifier)
      extends BaseUser with ClientUserAuthStubs with RelationshipStubs with DesStubs
}
