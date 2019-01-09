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

package uk.gov.hmrc.agentclientauthorisation.connectors

import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, NiExemptionRegistrationStub}
import uk.gov.hmrc.agentmtdidentifiers.model.{Eori, Utr}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class NiExemptionRegistrationConnectorISpec extends UnitSpec with AppAndStubs with NiExemptionRegistrationStub {

  val connector: NiExemptionRegistrationConnector = app.injector.instanceOf[NiExemptionRegistrationConnector]

  val utr = "9246624558"
  val eori = "AQ886940109600"
  val postcode = "BN11 4AW"

  "NiExemptionRegistrationConnector" should {
    "return the NI business if utr and postcode matches and user is enrolled" in {

      givenNiBusinessExists(utr, "FOO", Some(eori))

      val result = await(connector.getEori(Utr(utr), postcode))
      result shouldBe Some(
        GetEoriResponse(
          NiBusiness(
            name = "FOO",
            subscription = NiBusinessSubscription(status = "NI_SUBSCRIBED", eori = Some(Eori(eori))))))
    }

    "return the NI business if utr and postcode matches and user is NOT enrolled" in {

      givenNiBusinessExists(utr, "FOO", None)

      val result = await(connector.getEori(Utr(utr), postcode))
      result shouldBe Some(
        GetEoriResponse(
          NiBusiness(name = "FOO", subscription = NiBusinessSubscription(status = "NI_NOT_SUBSCRIBED", eori = None))))
    }

    "return the NI business if utr and postcode does not match" in {

      givenNiBusinessNotExists(utr)

      val result = await(connector.getEori(Utr(utr), postcode))
      result shouldBe None
    }

  }
}
