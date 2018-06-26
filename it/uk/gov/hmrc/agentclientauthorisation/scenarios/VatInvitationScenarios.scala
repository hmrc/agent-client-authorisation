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
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn

class VatInvitationScenarios
    extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors
    with Inside with Eventually {

  implicit val arn = RandomArn()
  val vrn: Vrn = Vrn("101747641")

  scenario("accept a VAT invitation") {
    val agency = new AgencyApi(this, arn, port)
    val client = new ClientApi(this, vrn, vrn, port)

    Given("An agent is logged in")
    given().client(clientId = vrn)
    given().agentAdmin(arn).isLoggedInAndIsSubscribed

    When("An agent sends invitations to Client")
    agency sendInvitation (clientId = vrn, service = "HMRC-MTD-VAT", clientIdType = "vrn", clientPostcode = None)

    And("Client accepts the first invitation")
    val stubs: Client =
      given().client(clientId = vrn).isLoggedInWithVATEnrolment(vrn).anMtdVatRelationshipIsCreatedWith(arn, vrn)
    val invitations = client.getInvitations()
    client.acceptInvitation(invitations.firstInvitation)

    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"

    stubs.verifyCallToCreateMtdVatRelationship(arn, vrn)
  }

  scenario("reject a VAT invitation") {
    val agency = new AgencyApi(this, arn, port)
    val client = new ClientApi(this, vrn, vrn, port)

    Given("An agent is logged in")
    given().client(clientId = vrn)
    given().agentAdmin(arn).isLoggedInAndIsSubscribed

    When("An agent sends invitations to Client")
    agency sendInvitation (clientId = vrn, service = "HMRC-MTD-VAT", clientIdType = "vrn", clientPostcode = None)

    And("Client rejects the first invitation")

    val stubs = given().client(clientId = vrn).isLoggedInWithVATEnrolment(vrn)
    val invitations = client.getInvitations()
    client.rejectInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Rejected"

    stubs.verifyNoCallsToCreateMtdVatRelationship
  }

}