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

package uk.gov.hmrc.agentclientauthorisation.sa.services

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.agentclientauthorisation.sa.connectors.{CesaDesignatoryDetailsAddress, CesaDesignatoryDetailsName, CesaIndividualsConnector, CesaTaxpayer}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class SaLookupServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  implicit val hc = HeaderCarrier()

  val connector = mock[CesaIndividualsConnector]
  val service = new SaLookupService(connector)
  val saUtr: SaUtr = SaUtr("0987654321")


  "lookupByUtrAndPostcode" should {
    "return name for a match" in {
      val cesaTaxpayer = CesaTaxpayer(
        name = CesaDesignatoryDetailsName(title = Some("Mr"), forename = Some("First"), surname = Some("Last")),
        address = CesaDesignatoryDetailsAddress(postcode = Some("AA1 1AA")))
      when(connector.taxpayer(saUtr)).thenReturn(Future successful Some(cesaTaxpayer))

      await(service.lookupByUtrAndPostcode(saUtr, "AA1 1AA")) shouldBe Some("Mr First Last")
    }

    "return None when the taxpayer was found but the postcode doesn't match" in {
      val cesaTaxpayer = CesaTaxpayer(
        name = CesaDesignatoryDetailsName(title = Some("Mr"), forename = Some("First"), surname = Some("Last")),
        address = CesaDesignatoryDetailsAddress(postcode = Some("AA1 1AA")))
      when(connector.taxpayer(saUtr)).thenReturn(Future successful Some(cesaTaxpayer))

      await(service.lookupByUtrAndPostcode(saUtr, "BA1 1AA")) shouldBe None
    }

    "return None when the taxpayer was not found" in {
      when(connector.taxpayer(saUtr)).thenReturn(Future successful None)

      await(service.lookupByUtrAndPostcode(saUtr, "AA1 1AA")) shouldBe None
    }

    "return empty string for a match when the taxpayer has None in all name fields" in {
      val cesaTaxpayer = CesaTaxpayer(
        name = CesaDesignatoryDetailsName(title = None, forename = None, surname = None),
        address = CesaDesignatoryDetailsAddress(postcode = Some("AA1 1AA")))
      when(connector.taxpayer(saUtr)).thenReturn(Future successful Some(cesaTaxpayer))

      await(service.lookupByUtrAndPostcode(saUtr, "AA1 1AA")) shouldBe Some("")
    }
  }

  override protected def beforeEach() = {
    super.beforeEach()
    reset(connector)
  }
}
