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

package uk.gov.hmrc.agentclientauthorisation.support

import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.domain.{AgentCode, Nino}
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

trait SecuredEndpointBehaviours extends AkkaMaterializerSpec {
  this: UnitSpec with AppAndStubs   =>


  def anEndpointAccessibleForMtdAgentsOnly(makeRequest: => HttpResponse): Unit = {
    "return 401 when the requester is an Agent but not authenticated" in {
      given().agentAdmin(RandomArn(), AgentCode("tehCode")).isNotLoggedIn()
      makeRequest.status shouldBe 401
      makeRequest.body shouldBe bodyOf(GenericUnauthorized)
    }

    "return 403 Forbidden when the requester is a logged as a NON MTD Agent" in {
      given().agentAdmin(RandomArn(), AgentCode("tehCode")).isLoggedIn().andIsNotSubscribedToAgentServices()
      makeRequest.status shouldBe 403
      makeRequest.body shouldBe bodyOf(AgentNotSubscribed)
    }
  }

  def anEndpointAccessibleForSaClientsOnly(id: Nino)(makeRequest: => HttpResponse): Unit = {
    "return 401 when the requester is not authenticated" in {
      given().client(clientId = id).isNotLoggedIn()
      makeRequest.status shouldBe 401
      makeRequest.body shouldBe bodyOf(GenericUnauthorized)
    }

  }
}
