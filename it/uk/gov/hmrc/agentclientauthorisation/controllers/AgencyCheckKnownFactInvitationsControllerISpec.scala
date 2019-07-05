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

import akka.stream.Materializer
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{AgentNotSubscribed, DateOfBirthDoesNotMatch, VatRegistrationDateDoesNotMatch}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class AgencyCheckKnownFactInvitationsControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
  }

  "GET /known-facts/individuals/nino/:nino/sa/postcode/:postcode" should {
    val request = FakeRequest("GET", "/known-facts/individuals/nino/:nino/sa/postcode/:postcode")

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

      status(result) shouldBe 400
    }

    "return 403 if Nino is known in ETMP but the postcode did not match or not found" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasABusinessPartnerRecordWithMtdItId(nino, mtdItId)

      val result = await(controller.checkKnownFactItsa(nino, "BN114AW")(request))
      status(result) shouldBe 403
    }
  }

  "GET /known-facts/individuals/:nino/dob/:dob" should {
    val request = FakeRequest("GET", "/known-facts/individuals/:nino/dob/:dob")

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
    val request = FakeRequest("GET", "/known-facts/organisations/vat/:vrn/registration-date/:vatRegistrationDate")

    "return No Content if Vrn is known in ETMP and the effectiveRegistrationDate matched" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasVatCustomerDetails(vrn, vatRegDate.toString, true)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      status(result) shouldBe 204
    }

    "return Vat Registration Date Does Not Match if Vrn is known in ETMP and the effectiveRegistrationDate did not match" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasVatCustomerDetails(vrn, "2019-01-01", true)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      result shouldBe VatRegistrationDateDoesNotMatch
    }

    "return Not Found if Vrn is unknown in ETMP" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      hasNoVatCustomerDetails(vrn)

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      status(result) shouldBe 404
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      givenAuditConnector()
      givenAgentNotSubscribed

      val result = await(controller.checkKnownFactVat(vrn, vatRegDate)(request))
      result shouldBe AgentNotSubscribed
    }
  }

}


