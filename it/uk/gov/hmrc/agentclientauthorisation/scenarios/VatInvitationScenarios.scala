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
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn

class VatInvitationScenarios
    extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with RelationshipStubs with Inspectors
    with Inside with Eventually {

  implicit val arn = RandomArn()
  val vrn: Vrn = Vrn("101747641")

  scenario("accept a VAT invitation") {
    val agency = new AgencyApi(this, arn, port)
    val client = new ClientApi(this, vrn, vrn, port)
    val emailInformation = (templateId: String) => EmailInformation(Seq("agent@email.com"),
      templateId,
      Map("agencyName"->"My Agency", "clientName" -> "GDT", "service" -> "submit their VAT returns through software."))

    Given("An agent is logged in")
    given().client(clientId = vrn).getAgencyEmailViaClient(arn)
    given().client(clientId = vrn).givenGetAgentNameViaClient(arn)
    given().client(clientId = vrn).getVatClientDetails(vrn)
    given().agentAdmin(arn).givenAuthorisedAsAgent(arn).givenGetAgentName(arn)

    When("An agent sends invitations to Client")
    agency sendInvitation (clientId = vrn, service = "HMRC-MTD-VAT", clientIdType = "vrn", clientPostcode = None)

    And("Client accepts the first invitation")
    given().client(clientId = vrn).givenClientVat(vrn)
    anMtdVatRelationshipIsCreatedWith(arn, vrn)
    given().client(clientId = vrn).givenEmailSent(emailInformation("client_accepted_authorisation_request"))

    val invitations = client.getInvitations()
    client.acceptInvitation(invitations.firstInvitation)

    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"

    verifyCallToCreateMtdVatRelationship(arn, vrn)
  }

  scenario("reject a VAT invitation") {
    val agency = new AgencyApi(this, arn, port)
    val client = new ClientApi(this, vrn, vrn, port)
    val emailInformation = (templateId: String) => EmailInformation(Seq("agent@email.com"),
      templateId,
      Map("agencyName"->"My Agency", "clientName" -> "GDT", "service" -> "submit their VAT returns through software."))

    Given("An agent is logged in")
    given().client(clientId = vrn).getAgencyEmailViaClient(arn)
    given().client(clientId = vrn).givenGetAgentNameViaClient(arn)
    given().client(clientId = vrn).getVatClientDetails(vrn)
    given().client(clientId = vrn).givenEmailSent(emailInformation("client_rejected_authorisation_request"))
    given().agentAdmin(arn).givenAuthorisedAsAgent(arn).givenGetAgentName(arn)

    When("An agent sends invitations to Client")
    agency sendInvitation (clientId = vrn, service = "HMRC-MTD-VAT", clientIdType = "vrn", clientPostcode = None)

    And("Client rejects the first invitation")

    given().client(clientId = vrn).givenClientVat(vrn)
    val invitations = client.getInvitations()
    client.rejectInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Rejected"

    verifyNoCallsToCreateMtdVatRelationship(arn, vrn)
  }

}
