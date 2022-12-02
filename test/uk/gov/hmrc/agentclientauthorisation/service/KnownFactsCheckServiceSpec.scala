/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.model.VatDetails
import uk.gov.hmrc.agentclientauthorisation.model.VatKnownFactCheckResult._
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.Vrn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class KnownFactsCheckServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {

  val desConnector: DesConnector = mock[DesConnector]
  val citizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  val service = new KnownFactsCheckService(desConnector, citizenDetailsConnector)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(desConnector)
  }

  "clientVatRegistrationDateMatches" should {
    val clientVrn = Vrn("101747641")

    "return VatKnownFactCheckOk if the client is solvent and the supplied date matches the effectiveRegistrationDate from the VAT Customer Information in ETMP" in {
      val vatRegDateInETMP = LocalDate.parse("2001-02-03")
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future successful Some(VatDetails(Some(vatRegDateInETMP), isInsolvent = false)))

      await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied)) shouldBe VatKnownFactCheckOk
    }

    "return VatKnownFactNotMatched if the client is solvent and the supplied date does not match the effectiveRegistrationDate from the VAT Customer Information in ETMP" in {
      val vatRegDateInETMP = LocalDate.parse("2001-02-04")
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future successful Some(VatDetails(Some(vatRegDateInETMP), isInsolvent = false)))

      await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied)) shouldBe VatKnownFactNotMatched
    }

    "return VatKnownFactNotMatched if the supplied date does not match the effectiveRegistrationDate from the VAT Customer Information in ETMP" in {
      val vatRegDateInETMP = LocalDate.parse("2001-02-04")
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future successful Some(VatDetails(Some(vatRegDateInETMP), isInsolvent = true)))

      await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied)) shouldBe VatKnownFactNotMatched
    }

    "return VatDetailsNotFound if the VAT Customer Information in ETMP does not contain an effectiveRegistrationDate" in {
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future successful Some(VatDetails(None, isInsolvent = true)))

      await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied)) shouldBe VatDetailsNotFound
    }

    "return VatRecordClientInsolvent if the supplied date matches the ETMP date but the client is insolvent" in {
      val vatRegDateInETMP = LocalDate.parse("2001-02-03")
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future successful Some(VatDetails(Some(vatRegDateInETMP), isInsolvent = true)))

      await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied)) shouldBe VatRecordClientInsolvent
    }

    "return VatDetailsNotFound if there is no VAT Customer Information in ETMP (client not subscribed)" in {
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future successful None)

      await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied)) shouldBe VatDetailsNotFound
    }

    "throw Upstream5xxResponse if DES is unavailable" in {
      val vatRegDateSupplied = LocalDate.parse("2001-02-03")

      when(desConnector.getVatDetails(clientVrn))
        .thenReturn(Future failed UpstreamErrorResponse("error", 502, 503))

      assertThrows[UpstreamErrorResponse] {
        await(service.clientVatRegistrationCheckResult(clientVrn, vatRegDateSupplied))
      }
    }
  }

  "clientDateOfBirthMatches" should {
    val clientNino = Nino("AE123456A")
    val format = DateTimeFormatter.ofPattern("ddMMyyyy")

    "return Some(true) if the supplied date of birth matches the date of birth returned from citizen details" in {
      val dateOfBirthInCitizenDetails = LocalDate.parse("03022001", format)
      val dateOfBirthSupplied = LocalDate.parse("03022001", format)

      when(citizenDetailsConnector.getCitizenDateOfBirth(clientNino))
        .thenReturn(Future successful Some(CitizenDateOfBirth(Some(dateOfBirthInCitizenDetails))))

      await(service.clientDateOfBirthMatches(clientNino, dateOfBirthSupplied)) shouldBe Some(true)
    }

    "return Some(false) if the supplied date of birth does not match the date of birth returned from citizen details" in {
      val dateOfBirthInCitizenDetails = LocalDate.parse("03022002", format)
      val dateOfBirthSupplied = LocalDate.parse("03022001", format)

      when(citizenDetailsConnector.getCitizenDateOfBirth(clientNino))
        .thenReturn(Future successful Some(CitizenDateOfBirth(Some(dateOfBirthInCitizenDetails))))

      await(service.clientDateOfBirthMatches(clientNino, dateOfBirthSupplied)) shouldBe Some(false)
    }

    "return None if there is not record in citizen details" in {
      val dateOfBirthSupplied = LocalDate.parse("03022001", format)

      when(citizenDetailsConnector.getCitizenDateOfBirth(clientNino))
        .thenReturn(Future successful None)

      await(service.clientDateOfBirthMatches(clientNino, dateOfBirthSupplied)) shouldBe None
    }

    "throw Upstream 5xx exception is citizen details is down" in {
      val dateOfBirthSupplied = LocalDate.parse("03022001", format)

      when(citizenDetailsConnector.getCitizenDateOfBirth(clientNino))
        .thenReturn(Future failed UpstreamErrorResponse("error", 502, 503))

      assertThrows[UpstreamErrorResponse] {
        await(service.clientDateOfBirthMatches(clientNino, dateOfBirthSupplied))
      }
    }
  }
}
