/*
 * Copyright 2017 HM Revenue & Customs
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
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class EtmpConnectorISpec extends UnitSpec with AppAndStubs {

  "getBusinessDetails" should {
    "return postcode and country code for a registered client" in {
      val client = given()
        .client()
        .hasABusinessPartnerRecord

      val response = await(connector.getBusinessDetails(client.nino.get)).get

      response.businessAddressDetails.countryCode shouldBe "GB"
      response.businessAddressDetails.postalCode shouldBe Some("AA11AA")
    }

    "return None if the client is not registered" in {
      val client = given()
        .client()
        .hasNoBusinessPartnerRecord

      val response = await(connector.getBusinessDetails(client.nino.get))

      response shouldBe None
    }
  }

  def connector = app.injector.instanceOf[EtmpConnector]

}
