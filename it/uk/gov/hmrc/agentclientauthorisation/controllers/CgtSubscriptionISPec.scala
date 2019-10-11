package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model._

class CgtSubscriptionISPec extends BaseISpec {

  implicit val mat = app.injector.instanceOf[Materializer]

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

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}")

      val result = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result) shouldBe 200
      jsonBodyOf(result).as[CgtSubscription] shouldBe cgtSubscription
    }

    "return cgt subscription as expected for Trustees" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      val cgtSub = cgtSubscription.copy(subscriptionDetails = cgtSubscription.subscriptionDetails.copy(typeOfPersonDetails = TypeOfPersonDetails("Trustee", Right(OrganisationName("some org")))))
      getCgtSubscription(cgtRef, 200, Json.toJson(cgtSub).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}")

      val result = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result) shouldBe 200
      jsonBodyOf(result).as[CgtSubscription] shouldBe cgtSub
    }

    "handle single 400 from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 400, Json.toJson(desError).toString())

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}")

      val result = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result) shouldBe 400
      jsonBodyOf(result).toString() shouldBe """[{"code":"INVALID_REGIME","reason":"some message"}]"""
    }

    "handle multiple 400s from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 400, desErrors)

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}")

      val result = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result) shouldBe 400
      jsonBodyOf(result)
        .toString() shouldBe """[{"code":"INVALID_REGIME","reason":"some message"},{"code":"INVALID_IDType","reason":"some other message"}]"""
    }

    "handle 404 from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getCgtSubscription(cgtRef, 404, notFoundJson)

      val request = FakeRequest("GET", s"/cgt/subscriptions/${cgtRef.value}")

      val result = await(controller.getCgtSubscriptionDetails(cgtRef)(request))
      status(result) shouldBe 404
      jsonBodyOf(result)
        .toString() shouldBe """[{"code":"NOT_FOUND","reason":"Data not found  for the provided Registration Number."}]"""
    }
  }
}
