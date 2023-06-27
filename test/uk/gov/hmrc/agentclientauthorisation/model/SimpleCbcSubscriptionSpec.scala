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

package uk.gov.hmrc.agentclientauthorisation.model

import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.connectors.SimpleCbcSubscription
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

class SimpleCbcSubscriptionSpec extends UnitSpec {

  val displaySubscriptionForCbCResponse: JsObject = Json.parse("""{
                                                                 |	"displaySubscriptionForCbCResponse": {
                                                                 |		"responseCommon": {
                                                                 |			"status": "OK",
                                                                 |			"processingDate": "2020-08-09T11:23:45Z"
                                                                 |		},
                                                                 |		"responseDetail": {
                                                                 |			"subscriptionID": "XYCBC2764649410",
                                                                 |			"tradingName": "Tools for Traders",
                                                                 |			"isGBUser": true,
                                                                 |			"primaryContact": {
                                                                 |					"email": "Tim@toolsfortraders.com",
                                                                 |					"phone": "078803423883",
                                                                 |					"mobile": "078803423883",
                                                                 |					"individual": {
                                                                 |						"lastName": "Taylor",
                                                                 |						"firstName": "Tim"
                                                                 |					}
                                                                 |			},
                                                                 |			"secondaryContact": {
                                                                 |				"email": "contact@toolsfortraders.com",
                                                                 |				"organisation": {
                                                                 |					"organisationName": "Tools for Traders Limited"
                                                                 |				}
                                                                 |			}
                                                                 |		}
                                                                 |	}
                                                                 |}""".stripMargin).as[JsObject]

  "SimpleCbcSubscriptionSpec" should {
    "parse from JSON correctly" in {
      SimpleCbcSubscription.fromDisplaySubscriptionForCbCResponse(displaySubscriptionForCbCResponse) shouldBe
        SimpleCbcSubscription(
          tradingName = Some("Tools for Traders"),
          otherNames = Seq("Tim Taylor", "Tools for Traders Limited"),
          isGBUser = true,
          emails = Seq("Tim@toolsfortraders.com", "contact@toolsfortraders.com")
        )
    }
    "return the trading name if available when asked for a name" in {
      SimpleCbcSubscription(
        tradingName = Some("Tools for Traders"),
        otherNames = Seq("Tim Taylor", "Tools for Traders Limited"),
        isGBUser = true,
        emails = Seq("Tim@toolsfortraders.com", "contact@toolsfortraders.com")
      ).anyAvailableName shouldBe Some("Tools for Traders")
    }
    "return any other available name if the trading name is not available" in {
      SimpleCbcSubscription(
        tradingName = None,
        otherNames = Seq("Tim Taylor", "Tools for Traders Limited"),
        isGBUser = true,
        emails = Seq("Tim@toolsfortraders.com", "contact@toolsfortraders.com")
      ).anyAvailableName shouldBe Some("Tim Taylor")
    }
  }
}
