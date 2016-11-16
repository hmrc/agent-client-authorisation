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

package uk.gov.hmrc.agentclientauthorisation.scenarios

import org.scalatest._
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.auth.microservice.connectors.Regime

class AgencyFilteringByClientIdISpec extends FeatureSpec with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors with Inside with Eventually {

  private implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val mtdClientId: MtdClientId = FakeMtdClientId.random()
  private val mtdClientId2: MtdClientId = FakeMtdClientId.random()
  private val MtdSaRegime: String = "mtd-sa"

  feature("Agencies can filter")  {

    scenario("on the status of clients invitations") {
      val agency = new AgencyApi(arn, port)
      val client1 = new ClientApi(mtdClientId, port)
      val client2 = new ClientApi(mtdClientId2, port)

      Given("An agent is logged in")
      given().agentAdmin(arn, agentCode).isLoggedInWithSessionId().andHasMtdBusinessPartnerRecord()

      And("the Agency has sent 1 invitation to 2 different clients")
      agencySendsSeveralInvitations(agency, MtdSaRegime)

      When(s"the Agency filters by client ID")
      Then(s"only the client matching that id is returned")
      agencyFiltersById(agency, client1.clientId)
      agencyFiltersById(agency, client2.clientId)
    }
  }

  private def agencySendsSeveralInvitations(agency: AgencyApi, regime: String): Unit = {
    val location1 = agency sendInvitation(mtdClientId, regime = regime)
    val location2 = agency sendInvitation(mtdClientId2, regime = regime)
    location1 should not(be(location2))

    info(s"the Agency should see 2 pending invitations for $mtdClientId and $mtdClientId2")
    val response = agency.sentInvitations()
    response.numberOfInvitations shouldBe 2

    val i1 = response.firstInvitation
    i1.arn shouldBe arn
    i1.clientId shouldBe mtdClientId
    i1.regime shouldBe Regime(regime)
    i1.status shouldBe "Pending"

    val i2 = response.secondInvitation
    i2.arn shouldBe arn
    i2.clientId shouldBe mtdClientId2
    i2.regime shouldBe Regime(regime)
    i2.status shouldBe "Pending"
    // TODO: This can only be implemented after the Play 2.5 upgrade 
//    val links = response.links
//    links.selfLink shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
//    links.invitations shouldBe 'nonEmpty
  }

  private def agencyFiltersById(agency: AgencyApi, clientId:MtdClientId): Unit = {
    val invitation = agency.sentInvitations(filteredBy = Seq("clientId" -> clientId.value))
    invitation.numberOfInvitations shouldBe 1
    invitation.firstInvitation.status shouldBe "Pending"
    invitation.firstInvitation.arn shouldBe agency.arn
    invitation.firstInvitation.clientId shouldBe clientId
  }
}
