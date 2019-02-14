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
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}

class ClientFiltersByStatusApiPlatformISpec
    extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with RelationshipStubs with Inspectors
    with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val nino: Nino = nextNino

  feature("Clients can filter") {

    scenario(s"on the status of invitations - ${Service.MtdIt.id}") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, mtdItId1, port)

      Given("An agent and a client are logged in")
      given().client(clientId = nino, canonicalClientId = mtdItId1)
        .hasABusinessPartnerRecordWithMtdItId(nino, mtdItId1)
      anMtdItRelationshipIsCreatedWith(arn, mtdItId1)
      given().agentAdmin(arn).givenAuthorisedAsAgent(arn)

      When("An agent sends several invitations")
      agencySendsSeveralInvitations(agency)((client, MtdItService), (client, MtdItService))

      Then(s"the Client should see 2 pending invitations from the Agency $arn")
      given().client(clientId = nino, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1)
      clientsViewOfPendingInvitations(client)

      When(s"the Client accepts the first Agency invitation")
      clientAcceptsFirstInvitation(client)

      Then(s"the Client filters their invitations by accepted")
      clientFiltersByAccepted(client)

      When("the Client rejects the second invitation")
      clientRejectsSecondInvitation(client)

      And("the Client filters their invitations by rejected")
      clientFiltersByRejected(client)

      And("the Client filters by multiple status")
      clientFiltersByMultipleStatuses(client)
    }

    scenario(s"on the status of invitations - ${Service.PersonalIncomeRecord.id}") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino1, nino1, port)

      Given("An agent and a client are logged in")
      given()
        .client(clientId = nino1, canonicalClientId = mtdItId1)
        .hasABusinessPartnerRecord(nino)
      anAfiRelationshipIsCreatedWith(arn, nino1)
      given().agentAdmin(arn).givenAuthorisedAsAgent(arn)

      When("An agent sends several invitations")
      agencySendsSeveralInvitations(agency)(
        (client, PersonalIncomeRecordService),
        (client, PersonalIncomeRecordService))

      Then(s"the Client should see 2 pending invitations from the Agency $arn")
      given().client(clientId = nino1, canonicalClientId = nino1).givenClientNi(nino1)
      clientsViewOfPendingInvitations(client, PersonalIncomeRecordService, "NI", nino1)

      When(s"the Client accepts the first Agency invitation")
      clientAcceptsFirstInvitation(client)

      Then(s"the Client filters their invitations by accepted")
      clientFiltersByAccepted(client)

      When("the Client rejects the second invitation")
      clientRejectsSecondInvitation(client)

      And("the Client filters their invitations by rejected")
      clientFiltersByRejected(client)

      And("the Client filters by multiple status")
      clientFiltersByMultipleStatuses(client)
    }
  }

  private def clientRejectsSecondInvitation(client: ClientApi): Unit = {
    val invitations = client.getInvitations(Seq("status" -> "pending"))
    client.rejectInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"
    refetchedInvitations.secondInvitation.status shouldBe "Rejected"
  }

  private def clientFiltersByRejected(client: ClientApi): Unit = {
    val pendingFiltered = client.getInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 0

    val rejectedFiltered = client.getInvitations(filteredBy = Seq("status" -> "rejected"))
    rejectedFiltered.numberOfInvitations shouldBe 1
    rejectedFiltered.firstInvitation.status shouldBe "Rejected"
  }

  private def clientFiltersByAccepted(client: ClientApi): Unit = {
    val pendingFiltered = client.getInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 1
    pendingFiltered.firstInvitation.status shouldBe "Pending"

    val acceptedFiltered = client.getInvitations(filteredBy = Seq("status" -> "accepted"))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status shouldBe "Accepted"
  }

  private def clientFiltersByMultipleStatuses(client: ClientApi): Unit = {
    val acceptedFiltered = client.getInvitations(filteredBy = Seq("status" -> "accepted", "status" -> "rejected"))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status shouldBe "Accepted"

    val rejectedFiltered = client.getInvitations(filteredBy = Seq("status" -> "rejected", "status" -> "accepted"))
    rejectedFiltered.numberOfInvitations shouldBe 1
    rejectedFiltered.firstInvitation.status shouldBe "Rejected"
  }
}
