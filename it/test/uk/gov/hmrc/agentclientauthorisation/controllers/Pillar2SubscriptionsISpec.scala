/*
 * Copyright 2024 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._

import java.time.LocalDate

class Pillar2SubscriptionsISpec extends BaseISpec {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  val suppliedDate = LocalDate.parse("2001-02-03")

  val desError = DesError("INVALID_REGIME", "some message")

  val desErrors =
    """
      |{
      |  "failures": [
      |    {
      |      "code": "INVALID_REGIME",
      |      "reason": "some message"
      |    },
      |    {
      |      "code": "INVALID_IDType",
      |      "reason": "some other message"
      |    }
      |  ]
      |}
    """.stripMargin

  val notFoundJson =
    """
      |{
      |  "code": "NOT_FOUND",
      |  "reason": "Data not found  for the provided Registration Number."
      |}
    """.stripMargin

  val pillar2Record =
    s"""
       |{
       |                 "plrReference": "${plrId.value}",
       |                 "upeDetails": {
       |                     "organisationName": "OrgName",
       |                     "registrationDate": "$suppliedDate",
       |                     "domesticOnly": false,
       |                     "filingMember": false
       |                 },
       |                 "accountingPeriod": {
       |                     "startDate": "2023-01-01",
       |                     "endDate": "2023-12-31"
       |                 },
       |                 "upeCorrespAddressDetails": {
       |                     "addressLine1": "1 North Street",
       |                     "countryCode": "GB"
       |                 },
       |                 "primaryContactDetails": {
       |                     "name": "Some Name",
       |                     "emailAddress": "name@email.com"
       |                 },
       |                 "accountStatus": {
       |                     "inactive" : false
       |                 }
       |}
       |""".stripMargin

  val pillar2Subscription = Pillar2Subscription("OrgName", suppliedDate)

  "GET /pillar2/subscription/plrReference" should {

    "return pillar2 subscription as expected for individuals" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getPillar2Subscription(plrId, 200, pillar2Record)

      val request = FakeRequest("GET", s"/pillar2/subscription/${plrId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getPillar2SubscriptionDetails(plrId)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[Pillar2Subscription] shouldBe pillar2Subscription
    }

    "handle single 400 from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getPillar2Subscription(plrId, 400, Json.toJson(desError).toString())

      val request = FakeRequest("GET", s"/pillar2/subscription/${plrId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getPillar2SubscriptionDetails(plrId)(request)
      status(result) shouldBe 400
      contentAsJson(result).toString() shouldBe """[{"code":"INVALID_REGIME","reason":"some message"}]"""
    }

    "handle multiple 400s from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getPillar2Subscription(plrId, 400, desErrors)

      val request = FakeRequest("GET", s"/pillar2/subscription/${plrId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getPillar2SubscriptionDetails(plrId)(request)
      status(result) shouldBe 400
      contentAsJson(result)
        .toString() shouldBe """[{"code":"INVALID_REGIME","reason":"some message"},{"code":"INVALID_IDType","reason":"some other message"}]"""
    }

    "handle any other from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getPillar2Subscription(plrId, 404, notFoundJson)

      val request = FakeRequest("GET", s"/pillar2/subscription/${plrId.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getPillar2SubscriptionDetails(plrId)(request)
      status(result) shouldBe 404
      contentAsJson(result)
        .toString() shouldBe """[{"code":"NOT_FOUND","reason":"Data not found  for the provided Registration Number."}]"""
    }
  }
}
