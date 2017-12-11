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
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}

class AgencyFiltersByServiceApiPlatformISpec extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val nino: Nino = nextNino

  feature("Agencies can filter")  {

    scenario("on the service of invitations") {
      val agency = new AgencyApi(this, arn, port)
      Given("An agent is logged in")
      given().agentAdmin(arn, agentCode).isLoggedInAndIsSubscribed
      given().client(clientId = nino).hasABusinessPartnerRecordWithMtdItId()
      val client = new ClientApi(this, nino, mtdItId1, port)

      When("An agent sends several invitations")
      agencySendsSeveralInvitations(agency)(
        (client, MtdItService),
        (client, MtdItService)
      )

      Then("The agent filters by HMRC-MTD-IT")
      agencyFiltersByMtdIt(agency)

      Then("The agent filters by PERSONAL_INCOME_RECORD")
      agencyFiltersByMtdVat(agency)
    }
  }

  def agencyFiltersByMtdIt(agency: AgencyApi) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("service" -> MtdItService))

    invitations.numberOfInvitations shouldBe 2
  }

  def agencyFiltersByMtdVat(agency: AgencyApi) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("service" -> Service.PersonalIncomeRecord.id))

    invitations.numberOfInvitations shouldBe 0
  }
}
