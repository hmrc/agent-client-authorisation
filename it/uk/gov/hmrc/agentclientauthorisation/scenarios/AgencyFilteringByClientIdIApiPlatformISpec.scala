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

package uk.gov.hmrc.agentclientauthorisation.scenarios

import org.scalatest._
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.mtdItId1
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.domain.{AgentCode, Nino}

class AgencyFilteringByClientIdIApiPlatformISpec
    extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors
    with Inside with Eventually {

  override val arn = RandomArn()

  private implicit val agentCode = AgentCode("LMNOP123456")

  private val nino: Nino = nextNino
  private val nino2: Nino = nextNino

  feature("Agencies can filter") {

    scenario("on the status of clients invitations") {
      val agency = new AgencyApi(this, arn, port)
      val client1 = new ClientApi(this, nino, mtdItId, port)
      val mtdItId2 = MtdItId("0023456789")
      val client2 = new ClientApi(this, nino2, mtdItId2, port)

      Given("An agent is logged in")
      given().agentAdmin(arn, agentCode).givenAuthorisedAsAgent(arn)
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId(nino, mtdItId1)
      given().client(clientId = nino2).hasABusinessPartnerRecordWithMtdItId(nino2, mtdItId2)

      And("the Agency has sent 1 invitation to 2 different clients")
      agencySendsSeveralInvitations(agency)((client1, MtdItService), (client2, MtdItService))

      When(s"the Agency filters by client ID")
      Then(s"only the client matching that id is returned")
      agencyFiltersById(agency, client2.clientId)
      agencyFiltersById(agency, client1.clientId)

    }
  }

  private def agencyFiltersById(agency: AgencyApi, clientId: ClientId): Unit = {
    val invitation = agency.sentInvitations(filteredBy = Seq("clientId" -> clientId.value))
    invitation.numberOfInvitations shouldBe 1
    invitation.firstInvitation.status shouldBe "Pending"
    invitation.firstInvitation.arn shouldBe agency.arn
    invitation.firstInvitation.clientId shouldBe clientId.value
  }
}
