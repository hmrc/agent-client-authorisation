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

class CgtSubscriptionISPec extends BaseISpec {

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

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

  "GET /cgt/subscriptions/:cgtRef" should {

    "return cgt subscription as expected for individuals" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getCgtSubscriptionDetails(cgtRef)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[CgtSubscription] shouldBe cgtSubscription
    }

    "return cgt subscription as expected for Trustees" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      val cgtSub = cgtSubscription.copy(subscriptionDetails =
        cgtSubscription.subscriptionDetails.copy(typeOfPersonDetails = TypeOfPersonDetails("Trustee", Right(OrganisationName("some org"))))
      )
      getCgtSubscription(cgtRef, 200, Json.toJson(cgtSub).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getCgtSubscriptionDetails(cgtRef)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[CgtSubscription] shouldBe cgtSub
    }

    "handle single 400 from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 400, Json.toJson(desError).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getCgtSubscriptionDetails(cgtRef)(request)
      status(result) shouldBe 400
      contentAsJson(result).toString() shouldBe """[{"code":"INVALID_REGIME","reason":"some message"}]"""
    }

    "handle multiple 400s from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 400, desErrors)

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getCgtSubscriptionDetails(cgtRef)(request)
      status(result) shouldBe 400
      contentAsJson(result)
        .toString() shouldBe """[{"code":"INVALID_REGIME","reason":"some message"},{"code":"INVALID_IDType","reason":"some other message"}]"""
    }

    "handle any other from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 404, notFoundJson)

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}").withHeaders("Authorization" -> "Bearer testtoken")

      val result = controller.getCgtSubscriptionDetails(cgtRef)(request)
      status(result) shouldBe 404
      contentAsJson(result)
        .toString() shouldBe """[{"code":"NOT_FOUND","reason":"Data not found  for the provided Registration Number."}]"""
    }
  }
}
