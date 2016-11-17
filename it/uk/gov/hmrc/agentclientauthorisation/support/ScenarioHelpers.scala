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

import org.scalatest._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.play.auth.microservice.connectors.Regime

trait ScenarioHelpers {
  me : FeatureSpec with Matchers =>

  def mtdClientId: MtdClientId
  def arn: Arn
  val MtdSaRegime: Regime = Regime("mtd-sa")

  def agencySendsSeveralInvitations(agency: AgencyApi)(firstClient:(MtdClientId, Regime), secondClient:(MtdClientId, Regime)): Unit = {

    val locations = Seq(firstClient, secondClient).map (i => agency sendInvitation(i._1, regime = i._2))

    val location1 = locations.head
    val location2 = locations(1)
    location1 should not(be(location2))

    info(s"the Agency should see 2 pending invitations to Client $mtdClientId")
    val response = agency.sentInvitations()
    response.numberOfInvitations shouldBe 2

    checkInvite(response.firstInvitation)(firstClient)
    checkInvite(response.secondInvitation)(secondClient)

    def checkInvite(invitation: EmbeddedInvitation)(expected:(MtdClientId, Regime)): Unit = {
      invitation.arn shouldBe arn
      invitation.clientId shouldBe expected._1
      invitation.regime shouldBe expected._2
      invitation.status shouldBe "Pending"
    }
  }

  def clientsViewOfPendingInvitations(client: ClientApi): Unit = {
    val clientResponse = client.getInvitations()

    val i1 = clientResponse.firstInvitation
    i1.arn shouldBe arn
    i1.clientId shouldBe mtdClientId
    i1.regime shouldBe MtdSaRegime
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
    i2.regime shouldBe MtdSaRegime
    i2.status shouldBe "Pending"
  }

  def clientAcceptsFirstInvitation(client: ClientApi): Unit = {
    val invitations = client.getInvitations()
    client.acceptInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"
    refetchedInvitations.secondInvitation.status shouldBe "Pending"
  }

}
