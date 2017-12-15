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

    scenario("on the service of MtdIt invitations") {
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
      agencyFiltersByServiceAndExpectsResultCount(agency, 2, Service.MtdIt)

      Then("The agent filters by PERSONAL_INCOME_RECORD")
      agencyFiltersByServiceAndExpectsResultCount(agency, 0, Service.PersonalIncomeRecord)
    }

    scenario("on the service of PersonalIncomeRecord invitations") {
      val agency = new AgencyApi(this, arn, port)
      Given("An agent is logged in")
      given().agentAdmin(arn, agentCode).isLoggedInAndIsSubscribed
      given().client(clientId = nino)
        .hasABusinessPartnerRecordWithMtdItId() // TODO should not need this stubbing
      val client = new ClientApi(this, nino, nino, port)

      When("An agent sends several invitations")
      agencySendsSeveralInvitations(agency)(
        (client, PersonalIncomeRecordService),
        (client, PersonalIncomeRecordService)
      )

      Then("The agent filters by HMRC-MTD-IT")
      agencyFiltersByServiceAndExpectsResultCount(agency, 0, Service.MtdIt)

      Then("The agent filters by PERSONAL_INCOME_RECORD")
      agencyFiltersByServiceAndExpectsResultCount(agency, 2, Service.PersonalIncomeRecord)
    }
  }

  def agencyFiltersByServiceAndExpectsResultCount(agency: AgencyApi, expectedResultCount: Int, service: Service) = {
    val invitations = agency.sentInvitations(filteredBy = Seq("service" -> service.id))

    invitations.numberOfInvitations shouldBe expectedResultCount
  }

}
