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
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}

class AgencyInvitesClientApiPlatformISpec
    extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with RelationshipStubs with Inspectors
    with Inside with Eventually {

  implicit val arn = RandomArn()
  val nino: Nino = nextNino

  feature("Agencies can filter") {

    scenario("on the status of clients invitations") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, mtdItId1, port)

      given()
        .client(clientId = nino, canonicalClientId = mtdItId1)
        .hasABusinessPartnerRecordWithMtdItId(nino, mtdItId1)
      anMtdItRelationshipIsCreatedWith(arn, mtdItId1)
      given().agentAdmin(arn).givenAuthorisedAsAgent(arn)

      When("the Agency sends 2 invitations to the Client")
      agencySendsSeveralInvitations(agency)((client, MtdItService), (client, MtdItService))

      Then(s"the Client should see 2 pending invitations from the Agency $arn")
      given().client(clientId = nino, canonicalClientId = mtdItId1).givenClientMtdItId(mtdItId1)
      clientsViewOfPendingInvitations(client)

      When(s"the Client accepts the first Agency invitation")
      clientAcceptsFirstInvitation(client)

      Then(s"the Agency filters their sent invitations by status")
      given().agentAdmin(arn).givenAuthorisedAsAgent(arn)
      agencyFiltersByStatus(agency, "accepted")
    }
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
