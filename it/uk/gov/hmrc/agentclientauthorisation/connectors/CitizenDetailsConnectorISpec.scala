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

import org.joda.time.{IllegalFieldValueException, LocalDate}
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, CitizenDetailsStub}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.BadRequestException
import uk.gov.hmrc.play.test.UnitSpec
import org.joda.time.format._

import scala.concurrent.ExecutionContext.Implicits.global

class CitizenDetailsConnectorISpec extends UnitSpec with AppAndStubs with CitizenDetailsStub {

  val connector: CitizenDetailsConnector = app.injector.instanceOf[CitizenDetailsConnector]
  val nino = Nino("AE123456A")
  val format: DateTimeFormatter = DateTimeFormat.forPattern("ddMMyyyy")

  "getCitizenDateOfBirth" should {
    "return the date of birth for a given nino" in {
      givenCitizenDetailsAreKnownFor(nino.value, "11121971")
      val result = await(connector.getCitizenDateOfBirth(nino))
      result.getOrElse(CitizenDateOfBirth(None)).dateOfBirth shouldBe Some(LocalDate.parse("1971-12-11"))
    }

    "return None if date is invalid" in {
      givenCitizenDetailsAreKnownFor(nino.value, "11131971")
      val result = await(connector.getCitizenDateOfBirth(nino))
      result.getOrElse(CitizenDateOfBirth(None)).dateOfBirth shouldBe None
    }

    "return None if the date is blank" in {
      givenCitizenDetailsAreKnownFor(nino.value, "")
      val result = await(connector.getCitizenDateOfBirth(nino))
      result.getOrElse(CitizenDateOfBirth(None)).dateOfBirth shouldBe None
    }

    "return None is the nino is not found" in {
      givenCitizenDetailsReturnsResponseFor(nino.value, 404)
      val result = await(connector.getCitizenDateOfBirth(nino))
      result.getOrElse(CitizenDateOfBirth(None)).dateOfBirth shouldBe None
    }

    "return None if nino is not valid" in {
      givenCitizenDetailsReturnsResponseFor(nino.value, 400)
      val result = await(connector.getCitizenDateOfBirth(nino))
      result.getOrElse(CitizenDateOfBirth(None)).dateOfBirth shouldBe None
    }

  }
}
