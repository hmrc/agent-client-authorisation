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

import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, DesStubs, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, PlrId, SuspensionDetails, Utr, Vrn}
import uk.gov.hmrc.http.UpstreamErrorResponse

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with AppAndStubs with DesStubs {

  "getVatCustomerInformation" should {
    val clientVrn = Vrn("101747641")

    "return VatCustomerInformation for a subscribed VAT customer" when {

      "effectiveRegistrationDate is present" in {
        hasVatCustomerDetails(clientVrn, Some("2017-04-01"))

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe Some(LocalDate.parse("2017-04-01"))
        vatCustomerInfo.isInsolvent shouldBe false
      }

      "effectiveRegistrationDate is present and customer is insolvent" in {
        hasVatCustomerDetails(clientVrn, Some("2017-04-01"), isInsolvent = true)

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe Some(LocalDate.parse("2017-04-01"))
        vatCustomerInfo.isInsolvent shouldBe true
      }

      "effectiveRegistrationDate is not present" in {
        hasVatCustomerDetails(clientVrn, None)

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe None
      }

      "there is no approvedInformation" in {
        hasVatCustomerDetailsWithNoApprovedInformation(clientVrn)

        val vatCustomerInfo = await(connector.getVatDetails(clientVrn)).get
        vatCustomerInfo.effectiveRegistrationDate shouldBe None
      }
    }

    "return None if the VAT client is not subscribed (404)" in {
      hasNoVatCustomerDetails(clientVrn)

      await(connector.getVatDetails(clientVrn)) shouldBe None
    }

    "record metrics for each call" in {
      hasVatCustomerDetails(clientVrn, Some("2017-04-01"))

      await(connector.getVatDetails(clientVrn)).get
      await(connector.getVatDetails(clientVrn))

      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-GetVatCustomerInformation-GET")
    }

    "throw UpstreamErrorResponse if DES is unavailable" in {
      failsVatCustomerDetails(clientVrn, withStatus = 502)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatDetails(clientVrn))
      }
    }

    "throw UpstreamErrorResponse if DES returns a 400 status (invalid vrn passed by client)" in {
      failsVatCustomerDetails(clientVrn, withStatus = 400)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatDetails(clientVrn))
      }
    }

    "throw UpstreamErrorResponse if DES returns a 403 status (MIGRATION)" in {
      failsVatCustomerDetails(clientVrn, withStatus = 403)

      assertThrows[UpstreamErrorResponse] {
        await(connector.getVatDetails(clientVrn))
      }
    }
  }

  "getPillar2CustomerInformation" should {
    val clientPlrId = PlrId("XAPLR2222222222")

    "return Pillar2CustomerInformation for a subscribed VAT customer" when {

      "effectiveRegistrationDate is present  and customer is active" in {
        hasPillar2CustomerDetails(clientPlrId, "2017-04-01")
        val pillar2CustomerInfo = await(ifconnector.getPillar2Details(clientPlrId)).response.toOption.get
        pillar2CustomerInfo.effectiveRegistrationDate shouldBe LocalDate.parse("2017-04-01")
        pillar2CustomerInfo.inactive shouldBe false
      }

      "effectiveRegistrationDate is present  and customer is inactive" in {
        hasPillar2CustomerDetails(clientPlrId, "2017-04-01", inactive = true)
        val pillar2CustomerInfo = await(ifconnector.getPillar2Details(clientPlrId)).response.toOption.get
        pillar2CustomerInfo.effectiveRegistrationDate shouldBe LocalDate.parse("2017-04-01")
        pillar2CustomerInfo.inactive shouldBe true
      }

      "record metrics for each call" in {
        hasPillar2CustomerDetails(clientPlrId, "2017-04-01")
        await(ifconnector.getPillar2Details(clientPlrId)).response.toOption.get
        await(ifconnector.getPillar2Details(clientPlrId))
        verifyTimerExistsAndBeenUpdated("ConsumedAPI-IF-getPillar2Subscription-GET")
      }

      "there is no approvedInformation" in {
        hasPillar2CustomerDetailsWithNoApprovedInformation(clientPlrId)
        val pillar2Error = await(ifconnector.getPillar2Details(clientPlrId)).response.left.toOption.get
        pillar2Error.httpResponseCode shouldBe NOT_FOUND
      }
    }

    "return pillar2Error with status 404  if the Pillar2 client is not subscribed (404)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = NOT_FOUND)
      val pillar2Error = await(ifconnector.getPillar2Details(clientPlrId)).response.left.toOption.get
      pillar2Error.httpResponseCode shouldBe NOT_FOUND
    }

    "throw UpstreamErrorResponse if DES returns a 400 status (invalid PlrId passed by client)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = 400)
      assertThrows[UpstreamErrorResponse](await(ifconnector.getPillar2Details(clientPlrId)))
    }

    "throw UpstreamErrorResponse if DES returns a 403 status (MIGRATION)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = 403)
      assertThrows[UpstreamErrorResponse](await(ifconnector.getPillar2Details(clientPlrId)))
    }

    "throw UpstreamErrorResponse if DES is unavailable (502)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = 502)
      assertThrows[UpstreamErrorResponse](await(ifconnector.getPillar2Details(clientPlrId)))
    }

  }

  "getPillar2Subscription" should {
    val clientPlrId = PlrId("XAPLR2222222222")

    "return a Pillar2 subscription record" in {
      hasPillar2CustomerDetails(clientPlrId, "2017-04-01")
      val pillar2Subscription = await(ifconnector.getPillar2Subscription(clientPlrId)).response.toOption.get
      pillar2Subscription.registrationDate shouldBe LocalDate.parse("2017-04-01")
      pillar2Subscription.organisationName shouldBe "OrgName"
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-IF-getPillar2Subscription-GET")
    }
    "return pillar2Error with status 404  if the Pillar2 client is not subscribed (404)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = NOT_FOUND)
      val pillar2Error = await(ifconnector.getPillar2Subscription(clientPlrId)).response.left.toOption.get
      pillar2Error.httpResponseCode shouldBe NOT_FOUND
    }
    "return pillar2Error with status 400 if DES returns a 400 status (invalid PlrId passed by client)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = BAD_REQUEST)
      val pillar2Error = await(ifconnector.getPillar2Subscription(clientPlrId)).response.left.toOption.get
      pillar2Error.httpResponseCode shouldBe BAD_REQUEST

    }

    "return pillar2Error with status 403 if DES returns a 403 status (MIGRATION)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = FORBIDDEN)
      val pillar2Error = await(ifconnector.getPillar2Subscription(clientPlrId)).response.left.toOption.get
      pillar2Error.httpResponseCode shouldBe FORBIDDEN

    }

    "return pillar2Error with status 502 if DES is unavailable (502)" in {
      failsPillar2CustomerDetails(clientPlrId, withStatus = BAD_GATEWAY)
      val pillar2Error = await(ifconnector.getPillar2Subscription(clientPlrId)).response.left.toOption.get
      pillar2Error.httpResponseCode shouldBe BAD_GATEWAY
    }
  }

  "GetAgencyDetails" should {
    "return agency details for a given ARN" in {

      givenDESRespondsWithValidData(Arn(arn), "My Agency Ltd")

      val result = await(connector.getAgencyDetails(Right(Arn(arn))))
      result shouldBe Some(
        AgentDetailsDesResponse(
          Some(Utr("01234567890")),
          Some(
            AgencyDetails(
              Some("My Agency Ltd"),
              Some("abc@xyz.com"),
              Some("07345678901"),
              Some(BusinessAddress("Matheson House", Some("Grange Central"), Some("Town Centre"), Some("Telford"), Some("TF3 4ER"), "GB"))
            )
          ),
          Some(SuspensionDetails(suspensionStatus = false, None))
        )
      )
    }

    "return None when DES responds with invalid data" in {
      givenDESRespondsWithoutValidData(Arn(arn))

      val result = await(connector.getAgencyDetails(Right(Arn(arn))))

      result shouldBe Some(AgentDetailsDesResponse(None, None, None))
    }
  }

  private def connector = app.injector.instanceOf[DesConnector]

  private def ifconnector: IfConnector = app.injector.instanceOf[IfConnector]
}
