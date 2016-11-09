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

import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, Inspectors}
import uk.gov.hmrc.agentclientauthorisation.model.MtdClientId
import uk.gov.hmrc.agentclientauthorisation.support.{FakeMtdClientId, MongoAppAndStubs, RandomArn}
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.auth.microservice.connectors.Regime
import uk.gov.hmrc.play.test.UnitSpec

class AgencyInvitesClientISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually {

  private implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val mtdClientId: MtdClientId = FakeMtdClientId.random()
  private val MtdSaRegime: String = "mtd-sa"

  "Agencies can filter on STATUS" in {

    val agency = new AgencyApi(arn, port)
    val client = new ClientApi(mtdClientId, port)

    given().agentAdmin(arn, agentCode).isLoggedInWithSessionId().andHasMtdBusinessPartnerRecord()
    given().client(clientId = mtdClientId).isLoggedInWithSessionId().aRelationshipIsCreatedWith(arn)

    info("the Agency sends several invitations to the Client")
    agencySendsSeveralInvitations(agency, MtdSaRegime)

    info(s"the Client should see 2 pending invitations from the Agency $arn")
    clientsViewOfPendingInvitations(client)

    info(s"the Client accepts the first Agency invitation")
    clientAcceptsFirstInvitation(client)

    info(s"the Agency filters their sent invitations by status")
    agencyFiltersByStatus(agency)
  }

  private def agencySendsSeveralInvitations(agency: AgencyApi, regime: String): Unit = {
    val location1 = agency sendInvitation(mtdClientId, regime = regime)
    val location2 = agency sendInvitation(mtdClientId, regime = regime)
    location1 should not(be(location2))

    info(s"the Agency should see 2 pending invitations to Client $mtdClientId")
    val response = agency.sentInvitations()
    response.numberOfInvitations shouldBe 2

    val i1 = response.firstInvitation
    i1.arn shouldBe arn
    i1.clientId shouldBe mtdClientId
    i1.regime shouldBe Regime(regime)
    i1.status shouldBe "Pending"

    val i2 = response.secondInvitation
    i2.arn shouldBe arn
    i2.clientId shouldBe mtdClientId
    i2.regime shouldBe Regime(regime)
    i2.status shouldBe "Pending"

    //    response.links.selfLink shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
    //    response.links.invitations shouldBe 'nonEmpty
  }

  private def clientsViewOfPendingInvitations(client: ClientApi): Unit = {
    val clientResponse = client.getInvitations()

    val i1 = clientResponse.firstInvitation
    i1.arn shouldBe arn
    i1.clientId shouldBe mtdClientId
    i1.regime shouldBe Regime(MtdSaRegime)
    i1.status shouldBe "Pending"

    val selfLink = i1.links.selfLink
    selfLink should startWith(s"/agent-client-authorisation/clients/${mtdClientId.value}/invitations/received/")
    i1.links.acceptLink shouldBe Some(s"$selfLink/accept")
    i1.links.rejectLink shouldBe Some(s"$selfLink/reject")
    i1.links.cancelLink shouldBe None
    i1.links.agencyLink.get should include(s"/agencies-fake/agencies/${arn.arn}")

    val i2 = clientResponse.secondInvitation
    i2.arn shouldBe arn
    i2.clientId shouldBe mtdClientId
    i2.regime shouldBe Regime(MtdSaRegime)
    i2.status shouldBe "Pending"
  }

  private def clientAcceptsFirstInvitation(client: ClientApi): Unit = {
    val invitations = client.getInvitations()
    client.acceptInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"
    refetchedInvitations.secondInvitation.status shouldBe "Pending"
  }

  private def agencyFiltersByStatus(agency: AgencyApi): Unit = {
    val pendingFiltered = agency.sentInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 1
    pendingFiltered.firstInvitation.status shouldBe "Pending"

    val acceptedFiltered = agency.sentInvitations(filteredBy = Seq("status" -> "accepted"))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status shouldBe "Accepted"
  }
}
