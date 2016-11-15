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
import uk.gov.hmrc.play.auth.microservice.connectors.Regime

trait ScenarioHelpers {
  me : FeatureSpec with Matchers =>

  def mtdClientId: MtdClientId
  def arn: Arn
  val MtdSaRegime: String = "mtd-sa"

  def agencySendsSeveralInvitations(agency: AgencyApi, regime: String): Unit = {
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
    // TODO: This can only be implemented after the Play 2.5 upgrade 
//    val links = response.links
//    links.selfLink shouldBe s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
//    links.invitations shouldBe 'nonEmpty
  }

  def clientsViewOfPendingInvitations(client: ClientApi): Unit = {
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
}
