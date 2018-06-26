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

package uk.gov.hmrc.agentclientauthorisation.service

import org.joda.time.LocalDate
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.http.{HeaderCarrier, Upstream5xxResponse}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class KnownFactsCheckServiceSpec
    extends UnitSpec
    with MockitoSugar
    with BeforeAndAfterEach
    with TransitionInvitation {
  val desConnector: DesConnector = mock[DesConnector]

  val service = new KnownFactsCheckService(desConnector)

  implicit val hc = HeaderCarrier()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(desConnector)
  }

  "clientVatRegistrationDateMatches" should {
    val clientVrn = Vrn("101747641")

    "return Some(true) if the supplied date matches the effectiveRegistrationDate from the VAT Customer Information in ETMP" in {
      val vatRegDateInETMP = LocalDate.parse("2001-02-03")
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatCustomerInformation(clientVrn))
        .thenReturn(
          Future successful Some(VatCustomerInfo(Some(vatRegDateInETMP))))

      await(service.clientVatRegistrationDateMatches(
        clientVrn,
        vatRegDateSupplied)) shouldBe Some(true)
    }

    "return Some(false) if the supplied date does not match the effectiveRegistrationDate from the VAT Customer Information in ETMP" in {
      val vatRegDateInETMP = LocalDate.parse("2001-02-04")
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatCustomerInformation(clientVrn))
        .thenReturn(
          Future successful Some(VatCustomerInfo(Some(vatRegDateInETMP))))

      await(service.clientVatRegistrationDateMatches(
        clientVrn,
        vatRegDateSupplied)) shouldBe Some(false)
    }

    "return Some(false) if the VAT Customer Information in ETMP does not contain an effectiveRegistrationDate" in {
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatCustomerInformation(clientVrn))
        .thenReturn(Future successful Some(VatCustomerInfo(None)))

      await(service.clientVatRegistrationDateMatches(
        clientVrn,
        vatRegDateSupplied)) shouldBe Some(false)
    }

    "return None if there is no VAT Customer Information in ETMP (client not subscribed)" in {
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatCustomerInformation(clientVrn))
        .thenReturn(Future successful None)

      await(service.clientVatRegistrationDateMatches(
        clientVrn,
        vatRegDateSupplied)) shouldBe None
    }

    "throw Upstream5xxResponse if DES is unavailable" in {
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatCustomerInformation(clientVrn))
        .thenReturn(Future failed Upstream5xxResponse("error", 502, 503))

      assertThrows[Upstream5xxResponse] {
        await(
          service.clientVatRegistrationDateMatches(clientVrn,
                                                   vatRegDateSupplied))
      }
    }
  }
}
