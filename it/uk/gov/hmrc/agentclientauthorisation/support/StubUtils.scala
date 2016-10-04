/*
 * Copyright 2016 HM Revenue & Customs
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

import uk.gov.hmrc.agentclientauthorisation.model.Arn

trait StubUtils {
  me: StartAndStopWireMock =>

  class PreconditionBuilder {
    def agentAdmin(arn: String): AgentAdmin = {
      new AgentAdmin(arn, oid = "556737e15500005500eaf68e")
    }

    def agentAdmin(arn: Arn): AgentAdmin = {
      agentAdmin(arn.arn)
    }

    def user(oid: String = "1234567890abcdef00000000"): UnknownUser = {
      new UnknownUser(oid)
    }

    def customer(oid: String = "556737e15500005500eaf68f"): Client = {
      new Client(oid)
    }
  }

  def given() = {
    new PreconditionBuilder()
  }

  class BaseUser extends WiremockAware {
    override def wiremockBaseUrl: String = me.wiremockBaseUrl
  }

  class AgentAdmin(override val arn: String,
                   override val oid: String)
    extends BaseUser with AgentAuthStubs[AgentAdmin]

  class UnknownUser(override val oid: String)
    extends BaseUser with UnknownUserAuthStubs[UnknownUser]

  class Client(override val oid: String)
    extends BaseUser with ClientUserAuthStubs[Client]
}
