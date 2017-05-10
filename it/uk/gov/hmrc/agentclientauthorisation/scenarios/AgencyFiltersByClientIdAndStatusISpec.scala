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

package uk.gov.hmrc.agentclientauthorisation.scenarios

import org.scalatest._
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}


class AgencyFiltersByClientIdAndStatusApiPlatformISpec extends AgencyFiltersByClientIdAndStatusISpec

class AgencyFiltersByClientIdAndStatusFrontendISpec extends AgencyFiltersByClientIdAndStatusISpec {
  override val apiPlatform: Boolean = false
}

trait AgencyFiltersByClientIdAndStatusISpec extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val nino: Nino = nextNino
  val nino2: Nino = nextNino

  feature("Agencies can filter")  {

    scenario("on the client id and status of invitations") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, port)
      Given("An agent is logged in")
      given().agentAdmin(arn, agentCode).isLoggedInWithSessionId().andHasMtdBusinessPartnerRecord()
      given().client(clientId = nino).isLoggedInWithSessionId().hasABusinessPartnerRecord().aRelationshipIsCreatedWith(arn)
      given().client(clientId = nino2).hasABusinessPartnerRecord()

      When("An agent sends invitations to Client 1")
      agencySendsSeveralInvitations(agency)(
        (nino, MtdItService),
        (nino, MtdItService)
      )

      And("Sends an invitations to Client 2")
      agency sendInvitation(nino2, MtdItService)

      And("Client 1 accepts the first invitation")
      clientAcceptsFirstInvitation(client)

      Then("The agent filters by Client 1 and Pending")
      agencyFiltersByClient1Pending(agency)

      Then("The agent filters by Client 2 and Accepted")
      agencyFiltersByClient2Accepted(agency)
    }
  }

  def agencyFiltersByClient1Pending(agency: AgencyApi) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("clientId" -> nino.value, "status" -> "Pending"))

    invitations.numberOfInvitations shouldBe 1
  }

  def agencyFiltersByClient2Accepted(agency: AgencyApi) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("clientId" -> nino2.value, "status" -> "Accepted"))

    invitations.numberOfInvitations shouldBe 0
  }
}
