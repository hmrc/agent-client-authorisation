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
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}

class AgencyInvitesClientApiPlatformISpec extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val nino: Nino = nextNino

  feature("Agencies can filter")  {

    scenario("on the status of clients invitations") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, mtdItId1, port)

      given().agentAdmin(arn, agentCode).isLoggedInAndIsSubscribed
      given().client(clientId = nino, canonicalClientId=mtdItId1).isLoggedIn.hasABusinessPartnerRecordWithMtdItId(client.mtdItId).aRelationshipIsCreatedWith(arn)

      When("the Agency sends 2 invitations to the Client")
      agencySendsSeveralInvitations(agency)(
        (client, MtdItService),
        (client, MtdItService)
      )

      Then(s"the Client should see 2 pending invitations from the Agency $arn")
      clientsViewOfPendingInvitations(client)

      When(s"the Client accepts the first Agency invitation")
      clientAcceptsFirstInvitation(client)

      Then(s"the Agency filters their sent invitations by status")
      agencyFiltersByStatus(agency, "accepted")
    }

    scenario("on cancelled status") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, mtdItId1, port)
      Given("An agent and a client are logged in")
      given().agentAdmin(arn, agentCode).isLoggedInAndIsSubscribed
      given().client(clientId = nino).isLoggedIn.hasABusinessPartnerRecord().aRelationshipIsCreatedWith(arn)
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()

      When("the Agency sends several invitations to the Client")
      agencySendsSeveralInvitations(agency)(
        (client, MtdItService),
        (client, MtdItService)
      )

      Then(s"the Client should see 2 pending invitations from the Agency $arn")
      clientsViewOfPendingInvitations(client)

      When(s"the agency cancels the invitation")
      agencyCancelsInvitation(agency)

      Then(s"the Agency filters their sent invitations by status")
      agencyFiltersByStatus(agency, "cancelled")
    }
  }

  private def agencyCancelsInvitation(agency: AgencyApi) = {
    val invitations = agency.sentInvitations()
    agency.cancelInvitation(invitations.firstInvitation)
    val refetchedInvitations = agency.sentInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Cancelled"
    refetchedInvitations.secondInvitation.status shouldBe "Pending"
  }


  private def agencyFiltersByStatus(agency: AgencyApi, status: String): Unit = {
    val pendingFiltered = agency.sentInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 1
    pendingFiltered.firstInvitation.status shouldBe "Pending"

    val acceptedFiltered = agency.sentInvitations(filteredBy = Seq("status" -> status))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status.toLowerCase shouldBe status
  }
}
