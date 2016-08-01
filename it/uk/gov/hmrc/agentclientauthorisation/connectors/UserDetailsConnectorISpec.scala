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

package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.agentclientauthorisation.WSHttp
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, UserDetailsStub}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.test.UnitSpec

class UserDetailsConnectorISpec extends UnitSpec with AppAndStubs with UserDetailsStub {

  val agentCode = AgentCode("")
  "userDetails" should {
    "include the name, lastName and agentFriendlyName when all are present" in {
      val user = given().agentAdmin("ABCDEF123456", name = "First", lastName = Some("Last"), agentFriendlyName = Some("MD Accountancy"))
      userExists(user)

      val userDetails = await(newUserDetailsConnector().userDetails(userDetailsUrl(user)))
      userDetails shouldBe UserDetails("First", Some("Last"), Some("MD Accountancy"))
    }

    "include the name when lastName and agentFriendlyName are not present" in {
      val user = given().user(name = "1st", lastName = None)
      userExists(user)

      val userDetails = await(newUserDetailsConnector().userDetails(userDetailsUrl(user)))
      userDetails shouldBe UserDetails("1st", None, None)
    }
  }

  def newUserDetailsConnector() = new UserDetailsConnector(WSHttp)

}
