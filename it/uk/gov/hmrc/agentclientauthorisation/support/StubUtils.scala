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
    def agentAdmin(
      agentCode: String,
      name: String = "FirstName",
      lastName: Option[String] = Some("LastName"),
      agentFriendlyName: Option[String] = Some("DDCW Accountancy Ltd")): AgentAdmin = {
      new AgentAdmin(agentCode, oid = "556737e15500005500eaf68e", name = name, lastName = lastName, agentFriendlyName = agentFriendlyName)
    }

    def agentAdmin(agentCode: AgentCode): AgentAdmin = {
      agentAdmin(agentCode.value)
    }

    def user(oid: String = "556737e15500005500eaf690", name: String = "FirstName", lastName: Option[String] = Some("LastName")): UnknownUser = {
      new UnknownUser(oid, name, lastName)
    }

    def client(oid: String = "556737e15500005500eaf68f"): Client = {
      new Client(oid)
    }
  }

  def given() = {
    new PreconditionBuilder()
  }

  class BaseUser(val oid: String, val name: String = "FirstName", val lastName: Option[String] = Some("LastName"))
    extends WiremockAware {
    override def wiremockBaseUrl: String = me.wiremockBaseUrl
    def agentFriendlyName: Option[String] = None
  }

  class AgentAdmin(override val agentCode: String, override val oid: String, override val name: String, override val lastName: Option[String], override val agentFriendlyName: Option[String])
    extends BaseUser(oid) with AgentAuthStubs[AgentAdmin]

  class UnknownUser(override val oid: String, override val name: String, override val lastName: Option[String])
    extends BaseUser(oid) with BasicUserAuthStubs[UnknownUser]

  class Client(override val oid: String)
    extends BaseUser(oid) with ClientUserAuthStubs[Client]
}
