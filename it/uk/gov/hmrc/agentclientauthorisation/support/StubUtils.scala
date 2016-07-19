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

import uk.gov.hmrc.domain.AgentCode

trait StubUtils {
  me: StartAndStopWireMock =>

  class PreconditionBuilder {
    def agentAdmin(agentCode: String): AgentAdmin = {
      new AgentAdmin(agentCode, oid = "556737e15500005500eaf68e")
    }

    def agentAdmin(agentCode: AgentCode): AgentAdmin = {
      agentAdmin(agentCode.value)
    }
  }

  def given() = {
    new PreconditionBuilder()
  }

  class AgentAdmin(override val agentCode: String,
                   override val oid: String)
    extends AuthStubs[AgentAdmin] {

    override def wiremockBaseUrl: String = me.wiremockBaseUrl
  }

}
