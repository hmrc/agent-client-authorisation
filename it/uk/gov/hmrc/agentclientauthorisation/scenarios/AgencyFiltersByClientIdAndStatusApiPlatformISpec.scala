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
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.mtdItId1
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.MtdItId
import uk.gov.hmrc.domain.{AgentCode, Nino}

class AgencyFiltersByClientIdAndStatusApiPlatformISpec
    extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors
    with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val nino: Nino = nextNino
  val nino2: Nino = nextNino

  feature("Agencies can filter") {

    scenario("on the client id and status of invitations") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, mtdItId1, port)
      val mtdItId2 = MtdItId("mtdItId2")
      val client2 = new ClientApi(this, nino2, mtdItId2, port)

      Given("An agent is logged in")
      given()
        .client(mtdItId1, nino)
        .hasABusinessPartnerRecordWithMtdItId(mtdItId1)
        .anMtdItRelationshipIsCreatedWith(arn)
      given().client(mtdItId2, nino2).hasABusinessPartnerRecordWithMtdItId(mtdItId2)
      given().agentAdmin(arn).isLoggedInAndIsSubscribed

      When("An agent sends invitations to Client 1")
      agencySendsSeveralInvitations(agency)((client, MtdItService), (client, MtdItService))

      And("Sends an invitations to Client 2")
      agency sendInvitation (nino2, MtdItService)

      And("Client 1 accepts the first invitation")
      given().client(mtdItId1, nino).isLoggedInWithMtdEnrolment
      clientAcceptsFirstInvitation(client)

      Then("The agent filters by Client 1 and Pending")
      given().agentAdmin(arn).isLoggedInAndIsSubscribed
      agencyFiltersByClient1Pending(mtdItId1, agency)

      Then("The agent filters by Client 2 and Accepted")
      agencyFiltersByClient2Accepted(mtdItId2, agency)
    }
  }

  def agencyFiltersByClient1Pending(mtdItId: MtdItId, agency: AgencyApi) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("clientId" -> mtdItId.value, "status" -> "Pending"))

    invitations.numberOfInvitations shouldBe 1
  }

  def agencyFiltersByClient2Accepted(mtdItId: MtdItId, agency: AgencyApi) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("clientId" -> mtdItId.value, "status" -> "Accepted"))

    invitations.numberOfInvitations shouldBe 0
  }
}
