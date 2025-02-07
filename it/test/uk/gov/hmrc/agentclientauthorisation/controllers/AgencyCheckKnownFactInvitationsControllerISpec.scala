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

package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}

import java.time.LocalDate

class AgencyCheckKnownFactInvitationsControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
    ()
  }

  "GET /known-facts/individuals/nino/:nino/sa/postcode/:postcode" should {
    val request = FakeRequest("GET", "/known-facts/individuals/nino/:nino/sa/postcode/:postcode").withHeaders("Authorization" -> "Bearer testtoken")

    "return No Content if Nino is known in ETMP and the postcode matched" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)

      val result = await(controller.checkKnownFactItsa(nino, postcode)(request))

      status(result) shouldBe 204
    }

    "return 400 if given invalid postcode" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)

      val result = await(controller.checkKnownFactItsa(nino, "AAAAAA")(request))

      result shouldBe postcodeFormatInvalid("The submitted postcode, AAAAAA, does not match the expected format.")
    }

    "return 400 if given postcode is not present" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val result = await(controller.checkKnownFactItsa(nino, "")(request))

      result shouldBe postcodeRequired("HMRC-MTD-IT")
    }

    "return 403 if there is no businessAddressDetails present in the DES response" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecordWithNoBusinessAddressDetails(nino)

      val result = await(controller.checkKnownFactItsa(nino, postcode)(request))

      result shouldBe PostcodeDoesNotMatch
    }

    "return 403 if Nino is known in ETMP but the postcode did not match or not found" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)

      val result = await(controller.checkKnownFactItsa(nino, "BN114AW")(request))

      result shouldBe PostcodeDoesNotMatch
    }

    "return 501 if given non-UK address" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecord(nino, "AA11AA", "PL")

      val result = await(controller.checkKnownFactItsa(nino, "AA11AA")(request))

      result shouldBe nonUkAddress("PL")
    }

    "return No Content if Nino is unknown in ETMP but found in CiD with SAUTR exists and postcodes match" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoBusinessPartnerRecord(nino)
      givenCitizenDetailsAreKnownFor(nino.value, "10041980", Some("1234567890"))
      givenDesignatoryDetailsAreKNownFor(nino.value, Some("AA11AA"))

      val result = await(controller.checkKnownFactItsa(nino, "AA11AA")(request))

      status(result) shouldBe 204
    }

    "return Forbidden if Nino is unknown in ETMP but found in CiD with SAUTR exists and postcodes do not match" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoBusinessPartnerRecord(nino)
      givenCitizenDetailsAreKnownFor(nino.value, "10041980", Some("1234567890"))
      givenDesignatoryDetailsAreKNownFor(nino.value, Some("AA11AX"))

      val result = await(controller.checkKnownFactItsa(nino, "AA11AA")(request))

      result shouldBe PostcodeDoesNotMatch
    }

    "return Forbidden if Nino is unknown in ETMP but found in CiD with no SAUTR" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoBusinessPartnerRecord(nino)
      givenCitizenDetailsAreKnownFor(nino.value, "10041980", None)
      givenDesignatoryDetailsAreKNownFor(nino.value, Some("AA11AX"))

      val result = await(controller.checkKnownFactItsa(nino, "AA11AA")(request))

      result shouldBe ClientRegistrationNotFound

    }

  }

  "GET /known-facts/individuals/:nino/dob/:dob" should {
    val request = FakeRequest("GET", "/known-facts/individuals/:nino/dob/:dob").withHeaders("Authorization" -> "Bearer testtoken")

    "return No Content if Nino is known in citizen details and the dateOfBirth matched" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenCitizenDetailsAreKnownFor(nino.value, "10041980")

      val result = await(controller.checkKnownFactIrv(nino, dateOfBirth)(request))

      status(result) shouldBe 204
    }

    "return Date Of Birth Does Not Match if Nino is known in citizen details and the dateOfBirth did not match" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenCitizenDetailsAreKnownFor(nino.value, "10041981")

      val result = await(controller.checkKnownFactIrv(nino, dateOfBirth)(request))

      result shouldBe DateOfBirthDoesNotMatch

    }

    "return Not Found if Nino is unknown in citizen details" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      givenCitizenDetailsReturnsResponseFor(nino.value, 404)

      val result = await(controller.checkKnownFactIrv(nino, dateOfBirth)(request))

      status(result) shouldBe 404
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      givenAuditConnector()
      givenAgentNotSubscribed

      val result = await(controller.checkKnownFactIrv(nino, dateOfBirth)(request))

      result shouldBe AgentNotSubscribed
    }
  }

  "GET /known-facts/organisations/vat/:vrn/registration-date/:vatRegistrationDate" should {
    val request = FakeRequest("GET", "/known-facts/organisations/vat/:vrn/registration-date/:vatRegistrationDate").withHeaders(
      "Authorization" -> "Bearer testtoken"
    )

    "return No Content if Vrn is known in ETMP and the effectiveRegistrationDate matched" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasVatCustomerDetails(vrn, Some(vatRegDate.toString))

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      status(result) shouldBe 204
    }

    "return Vat Registration Date Does Not Match if Vrn is known in ETMP and the effectiveRegistrationDate did not match" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasVatCustomerDetails(vrn, Some("2019-01-01"), true)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      result shouldBe VatRegistrationDateDoesNotMatch
    }

    "return VatClientInsolvent if the Vrn is known in ETMP and the effectiveRegistrationDate matches but the client is insolvent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasVatCustomerDetails(vrn, Some("2018-01-01"), true)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      result shouldBe VatClientInsolvent
    }

    "return Not Found if Vrn is unknown in ETMP" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoVatCustomerDetails(vrn)
      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))

      status(result) shouldBe 404
    }
    "return Not Found if Vrn is without any 'approvedInformation' present" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasVatCustomerDetailsWithNoApprovedInformation(vrn)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      status(result) shouldBe 404
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      givenAuditConnector()
      givenAgentNotSubscribed

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      result shouldBe AgentNotSubscribed
    }

    "return 502 when DES/ETMP is unavailable" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      failsVatCustomerDetails(vrn, 503)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      status(result) shouldBe 502
    }
  }

  "GET /known-facts/ppt/:pptRegistrationNumber/:pptDateOfApplication" should {
    val request =
      FakeRequest("GET", "/known-facts/ppt/:pptRegistrationNumber/:pptDateOfApplication").withHeaders("Authorization" -> "Bearer testtoken")

    "return No Content if PptRef is known in ETMP and the dateOfApplication matched" in {
      givenAuditConnector()
      givenPptSubscription(pptRef, true, true, false)

      val result = await(controller.checkKnownFactPpt(pptRef, pptApplicationDate)(request))
      status(result) shouldBe 204
    }

    "return No Content when deregistrationDate is not on record" in {
      givenAuditConnector()
      givenPptSubscription(pptRef, true, false, false)

      val result = await(controller.checkKnownFactPpt(pptRef, pptApplicationDate)(request))
      status(result) shouldBe 204
    }

    "return Not Found if PptRef is unknown in ETMP" in {
      givenAuditConnector()
      givenPptSubscriptionRespondsWith(pptRef, 404)
      val result = await(controller.checkKnownFactPpt(pptRef, pptApplicationDate)(request))

      result shouldBe PptSubscriptionNotFound
    }
    "return Forbidden if PPT customer is deregistered" in {
      givenAuditConnector()
      givenPptSubscription(pptRef, true, true, true)

      val result = await(controller.checkKnownFactPpt(pptRef, pptApplicationDate)(request))
      result shouldBe PptCustomerDeregistered
    }

    "return Forbidden if date of application does not match" in {
      givenAuditConnector()
      givenPptSubscription(pptRef, false, false, false)

      val result = await(controller.checkKnownFactPpt(pptRef, LocalDate.parse("2019-10-10"))(request))
      result shouldBe PptRegistrationDateDoesNotMatch
    }

    "return 500 when DES/ETMP is unavailable" in {
      givenAuditConnector()
      givenPptSubscriptionRespondsWith(pptRef, 503)

      val result = await(controller.checkKnownFactPpt(pptRef, pptApplicationDate)(request))
      status(result) shouldBe 500
    }
  }

}
