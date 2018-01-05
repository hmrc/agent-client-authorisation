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

package uk.gov.hmrc.agentclientauthorisation.support

import org.scalatest._
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentclientauthorisation.support.EmbeddedSection.EmbeddedInvitation
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.mtdItId1
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier

trait ScenarioHelpers extends ApiRequests with Matchers with Eventually {

  self : FeatureSpec =>

  def mtdItId: TaxIdentifier = mtdItId1
  def arn: Arn
  val MtdItService = Service.MtdIt.id
  val PersonalIncomeRecordService = Service.PersonalIncomeRecord.id

  def agencySendsSeveralInvitations(agency: AgencyApi)(firstClient:(ClientApi, String), secondClient:(ClientApi, String)): Unit = {

    val locations = Seq(firstClient, secondClient).map (i => agency sendInvitation(i._1.suppliedClientId, service = i._2))

    val location1 = locations.head
    val location2 = locations(1)
    location1 should not(be(location2))

    info(s"the Agency should see 2 pending invitations to Client $mtdItId")
    val response = agency.sentInvitations()
    response.numberOfInvitations shouldBe 2

    checkInvite(response.firstInvitation)(firstClient._1.clientId, firstClient._2)
    checkInvite(response.secondInvitation)(secondClient._1.clientId, secondClient._2)

    def checkInvite(invitation: EmbeddedInvitation)(expectedClientId:ClientId, expectedService: String): Unit = {
      invitation.arn shouldBe arn
      invitation.clientIdType shouldBe expectedClientId.typeId
      invitation.clientId shouldBe expectedClientId.value
      invitation.service shouldBe expectedService
      invitation.status shouldBe "Pending"
    }

    val links = response.links
    links.selfLink shouldBe s"/agent-client-authorisation/agencies/${arn.value}/invitations/sent"
    links.invitations shouldBe 'nonEmpty
    links.invitations.head shouldBe response.firstInvitation.links.selfLink
    links.invitations(1) shouldBe response.secondInvitation.links.selfLink
  }

  def clientsViewOfPendingInvitations(client: ClientApi, service: String = MtdItService,
                                      taxIdType: String = "MTDITID", taxId: TaxIdentifier = mtdItId): Unit = {
    val clientResponse = client.getInvitations()

    val i1 = clientResponse.firstInvitation
    i1.arn shouldBe arn
    i1.clientId shouldBe client.clientId.value
    i1.service shouldBe service
    i1.status shouldBe "Pending"

    val selfLink = i1.links.selfLink
    selfLink should startWith(s"/agent-client-authorisation/clients/$taxIdType/${taxId.value}/invitations/received/")
    i1.links.acceptLink shouldBe Some(s"$selfLink/accept")
    i1.links.rejectLink shouldBe Some(s"$selfLink/reject")
    i1.links.cancelLink shouldBe None

    val i2 = clientResponse.secondInvitation
    i2.arn shouldBe arn
    i2.clientId shouldBe client.clientId.value
    i2.service shouldBe service
    i2.status shouldBe "Pending"
    val links = clientResponse.links
    links.selfLink shouldBe s"/agent-client-authorisation/clients/$taxIdType/${taxId.value}/invitations/received"
    links.invitations shouldBe 'nonEmpty
    links.invitations.head shouldBe i1.links.selfLink
    links.invitations(1) shouldBe i2.links.selfLink
  }

  def clientAcceptsFirstInvitation(client: ClientApi): Unit = {
    val invitations = client.getInvitations()
    client.acceptInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"
    refetchedInvitations.secondInvitation.status shouldBe "Pending"
  }
}
