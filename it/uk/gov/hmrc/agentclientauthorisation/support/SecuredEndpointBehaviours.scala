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

import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.domain.AgentCode
import uk.gov.hmrc.play.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

trait SecuredEndpointBehaviours {
  this: UnitSpec with AppAndStubs =>

  def anEndpointAccessibleForMtdAgentsOnly(request: => HttpResponse)(implicit me: Arn): Unit = {
    "return 401 when the requester is not authenticated" in {
      given().user().isNotLoggedIn()
      request.status shouldBe 401
    }

    "return 401 when user is not an MTD agent" in {
      given().client().isLoggedIn("0123456789")
      request.status shouldBe 401
    }
  }

  def anEndpointAccessibleForSaClientsOnly(request: => HttpResponse)(implicit me: Arn): Unit = {
    "return 401 when the requester is not authenticated" in {
      given().client().isNotLoggedIn()
      request.status shouldBe 401
    }

    "return 401 when user has no SA account" in {
      given().agentAdmin(me, AgentCode("12345")).isLoggedIn().andHasMtdBusinessPartnerRecord()
      request.status shouldBe 401
    }
  }
}
