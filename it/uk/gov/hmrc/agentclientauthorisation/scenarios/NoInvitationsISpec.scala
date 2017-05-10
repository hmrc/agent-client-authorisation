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

import org.scalatest.concurrent.Eventually
import org.scalatest.{Inside, Inspectors}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.test.UnitSpec

class NoInvitationsApiPlatformISpec extends NoInvitationsISpec

class NoInvitationsFrontendISpec extends NoInvitationsISpec {
  override val apiPlatform: Boolean = false
}

trait NoInvitationsISpec extends UnitSpec with MongoAppAndStubs with Inspectors with Inside with Eventually with ApiRequests {

  private implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  private val nino: Nino = nextNino

  "Before the Agency has sent any invitations" in {
    val agency = new AgencyApi(this, arn, port)
    val client = new ClientApi(this, nino, port)

    given().agentAdmin(arn, agentCode).isLoggedInWithSessionId().andIsSubscribedToAgentServices()
    given().client(clientId = nino).isLoggedInWithSessionId()

    info("the Agency sent invitations should be empty")
    val agencyResponse = agency.sentInvitations()
    agencyResponse.numberOfInvitations shouldBe 0
    agencyResponse.links.invitations shouldBe 'empty
    agencyResponse.links.selfLink shouldBe s"/agent-client-authorisation/agencies/${arn.value}/invitations/sent"
    agencyResponse.embedded.isEmpty shouldBe true

    info("the Clients received invitations should be empty")
    val clientResponse = client.getInvitations()
    clientResponse.numberOfInvitations shouldBe 0
    clientResponse.links.invitations shouldBe 'empty
    clientResponse.links.selfLink shouldBe s"/agent-client-authorisation/clients/ni/${nino.value}/invitations/received"
    clientResponse.embedded.isEmpty shouldBe true
  }
}
