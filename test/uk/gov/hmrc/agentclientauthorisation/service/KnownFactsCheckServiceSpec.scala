/*
 * Copyright 2023 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.model.Pillar2KnownFactCheckResult.{Pillar2DetailsNotFound, Pillar2KnownFactCheckOk, Pillar2KnownFactNotMatched, Pillar2RecordClientInactive}
import uk.gov.hmrc.agentclientauthorisation.model.VatKnownFactCheckResult._
import uk.gov.hmrc.agentclientauthorisation.model.{Pillar2Details, Pillar2DetailsResponse, Pillar2Error, VatDetails}
import uk.gov.hmrc.agentclientauthorisation.support.{TransitionInvitation, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.{PlrId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class KnownFactsCheckServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {

  val desConnector: DesConnector = mock[DesConnector]
  val ifConnector: IfConnector = mock[IfConnector]
  val citizenDetailsConnector: CitizenDetailsConnector = mock[CitizenDetailsConnector]

  val service = new KnownFactsCheckService(desConnector, ifConnector, citizenDetailsConnector)

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

  "clientPillar2RegistrationDateMatches" should {
    val clientPlrId = PlrId("XAPLR2222222222")

    "return Pillar2KnownFactCheckOk if the client is active and the supplied date matches the effectiveRegistrationDate from the Pillar2 Customer Information in ETMP" in {
      val pillar2RegDateInETMP = LocalDate.parse("2001-02-03")
      val pillar2RegDateSupplied = LocalDate.parse("2001-02-03")

      when(ifConnector.getPillar2Details(clientPlrId))
        .thenReturn(Future successful Pillar2DetailsResponse(Right(Pillar2Details(pillar2RegDateInETMP, false))))

      await(service.clientPillar2RegistrationCheckResult(clientPlrId, pillar2RegDateSupplied)) shouldBe Pillar2KnownFactCheckOk
    }

    "return Pillar2RecordClientInactive if the client is inactive and the supplied date matches the effectiveRegistrationDate from the Pillar2 Customer Information in ETMP" in {
      val pillar2RegDateInETMP = LocalDate.parse("2001-02-03")
      val pillar2RegDateSupplied = LocalDate.parse("2001-02-03")

      when(ifConnector.getPillar2Details(clientPlrId))
        .thenReturn(Future successful Pillar2DetailsResponse(Right(Pillar2Details(pillar2RegDateInETMP, true))))

      await(service.clientPillar2RegistrationCheckResult(clientPlrId, pillar2RegDateSupplied)) shouldBe Pillar2RecordClientInactive
    }

    "return Pillar2KnownFactNotMatched if the client is active and the supplied date does not match the effectiveRegistrationDate from the Pillar2 Customer Information in ETMP" in {
      val pillar2RegDateInETMP = LocalDate.parse("2001-02-04")
      val pillar2RegDateSupplied = LocalDate.parse("2001-02-03")

      when(ifConnector.getPillar2Details(clientPlrId))
        .thenReturn(Future successful Pillar2DetailsResponse(Right(Pillar2Details(pillar2RegDateInETMP, false))))

      await(service.clientPillar2RegistrationCheckResult(clientPlrId, pillar2RegDateSupplied)) shouldBe Pillar2KnownFactNotMatched
    }

    "return Pillar2RecordClientInactive if the client is inactive and the supplied date does not match the effectiveRegistrationDate from the Pillar2 Customer Information in ETMP" in {
      val pillar2RegDateInETMP = LocalDate.parse("2001-02-04")
      val pillar2RegDateSupplied = LocalDate.parse("2001-02-03")

      when(ifConnector.getPillar2Details(clientPlrId))
        .thenReturn(Future successful Pillar2DetailsResponse(Right(Pillar2Details(pillar2RegDateInETMP, true))))

      await(service.clientPillar2RegistrationCheckResult(clientPlrId, pillar2RegDateSupplied)) shouldBe Pillar2RecordClientInactive
    }

    "return Pillar2DetailsNotFound if the Pillar2 Customer Information in ETMP  return Error" in {
      val pillar2RegDateSupplied = LocalDate.parse("2001-02-03")

      when(ifConnector.getPillar2Details(clientPlrId))
        .thenReturn(Future successful Pillar2DetailsResponse(Left(Pillar2Error(402, Seq.empty))))

      await(service.clientPillar2RegistrationCheckResult(clientPlrId, pillar2RegDateSupplied)) shouldBe Pillar2DetailsNotFound
    }

    "throw Upstream5xxResponse if DES is unavailable" in {
      val pillar2RegDateSupplied = LocalDate.parse("2001-02-03")

      when(ifConnector.getPillar2Details(clientPlrId))
        .thenReturn(Future failed UpstreamErrorResponse("error", 502, 503))

      assertThrows[UpstreamErrorResponse] {
        await(service.clientPillar2RegistrationCheckResult(clientPlrId, pillar2RegDateSupplied))
      }
    }
  }
}
