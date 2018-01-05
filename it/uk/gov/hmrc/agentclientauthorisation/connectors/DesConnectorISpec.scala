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

import uk.gov.hmrc.agentclientauthorisation.support.AppAndStubs
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class DesConnectorISpec extends UnitSpec with AppAndStubs {

  "getBusinessDetails" should {
    "return postcode and country code for a registered client" in {
      val client = given()
        .client()
        .hasABusinessPartnerRecord()

      val response = await(connector.getBusinessDetails(client.clientId)).get

      response.businessData.head.businessAddressDetails.countryCode shouldBe "GB"
      response.businessData.head.businessAddressDetails.postalCode shouldBe Some("AA11AA")
      response.mtdbsa shouldBe None
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getRegistrationBusinessDetailsByNino-GET")
    }

    "return None if the client is not registered" in {
      val client = given()
        .client()
        .hasNoBusinessPartnerRecord

      val response = await(connector.getBusinessDetails(client.clientId))

      response shouldBe None
    }
  }

  "getMtdItId" should {
    "return mtdItId for a registered client" in {
      val client = given()
        .client()
        .hasABusinessPartnerRecordWithMtdItId(mtdItId1)

      val response = await(connector.getBusinessDetails(client.clientId)).get

      response.businessData.head.businessAddressDetails.countryCode shouldBe "GB"
      response.businessData.head.businessAddressDetails.postalCode shouldBe Some("AA11AA")
      response.mtdbsa shouldBe Some(mtdItId1)
      verifyTimerExistsAndBeenUpdated("ConsumedAPI-DES-getRegistrationBusinessDetailsByNino-GET")
    }

    "return mtdItId if the business data is empty" in {
      val client = given()
        .client()
        .hasBusinessPartnerRecordWithEmptyBusinessData(mtdItId1)

      val response = await(connector.getBusinessDetails(client.clientId)).get

      response.businessData.size shouldBe 0
      response.mtdbsa shouldBe Some(mtdItId1)
    }
  }

  private def connector = app.injector.instanceOf[DesConnector]

}
