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

package uk.gov.hmrc.agentclientauthorisation.sa.connectors

import uk.gov.hmrc.agentclientauthorisation.{MicroserviceGlobal, WSHttp}
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, CesaStubs}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.test.UnitSpec

class CesaIndividualsConnectorISpec extends UnitSpec with AppAndStubs with ServicesConfig {
  lazy val connector = MicroserviceGlobal.cesaIndividualsConnector

  "taxpayer" should {
    "return the taxpayer when CESA returns one" in {
      val saUtr = SaUtr("1234567890")
      CesaStubs.saTaxpayerExists(saUtr)
      val maybeCesaTaxpayer: Option[CesaTaxpayer] = await(connector.taxpayer(saUtr))
      maybeCesaTaxpayer shouldBe Some(CesaTaxpayer(
        name = CesaDesignatoryDetailsName(title = Some("Mr"), forename = Some("First"), surname = Some("Last")),
        address = CesaDesignatoryDetailsAddress(postcode = Some("AA1 1AA"))))
    }

    "return None when CESA returns a 404" in {
      val saUtr = SaUtr("1234567890")
      val maybeCesaTaxpayer: Option[CesaTaxpayer] = await(connector.taxpayer(saUtr))
      maybeCesaTaxpayer shouldBe None
    }
  }
}
