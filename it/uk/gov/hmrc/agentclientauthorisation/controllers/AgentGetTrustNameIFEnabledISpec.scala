package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._

class AgentGetTrustNameIFEnabledISpec extends BaseISpec {

  implicit val mat = app.injector.instanceOf[Materializer]

  override protected def additionalConfiguration =
    super.additionalConfiguration + ("mongodb.uri" -> mongoUri, "des-if.enabled" -> true)


  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  "GET /known-facts/organisations/trust/:utr" should {

    val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

    val invalidTrustJson =
      """{"code": "INVALID_TRUST_STATE","reason": "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible."}"""

    val notFoundJson =
      """{"code": "RESOURCE_NOT_FOUND","reason": "The remote endpoint has indicated that the trust is not found."}"""

    val request = FakeRequest("GET", s"/known-facts/organisations/trust/${utr.value}")

    "return success response for a given utr" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustNameIF(utr, 200, trustNameJson)

      val result = controller.getTrustName(utr.value)(request)
      status(result) shouldBe 200

      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))

    }

    "return success response if trust is with in INVALID_TRUST_STATE for a given utr" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustNameIF(utr, 400, invalidTrustJson)

      val result = controller.getTrustName(utr.value)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(
        Left(
          InvalidTrust(
            "INVALID_TRUST_STATE",
            "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible.")))
    }

    "handles 404 response from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustNameIF(utr, 404, notFoundJson)

      val result = controller.getTrustName(utr.value)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(
        Left(InvalidTrust("RESOURCE_NOT_FOUND", "The remote endpoint has indicated that the trust is not found.")))
    }
  }
  "GET /known-facts/organisations/trust/:urn" should {

    val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""

    val invalidTrustJson =
      """{"code": "INVALID_TRUST_STATE","reason": "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible."}"""

    val notFoundJson =
      """{"code": "RESOURCE_NOT_FOUND","reason": "The remote endpoint has indicated that the trust is not found."}"""

    val request = FakeRequest("GET", s"/known-facts/organisations/trust/${urn.value}")

    "return success response for a given urn" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustNameIF(urn, 200, trustNameJson)

      val result = controller.getTrustName(urn.value)(request)
      status(result) shouldBe 200

      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(Right(TrustName("Nelson James Trust")))

    }

    "return success response if trust is with in INVALID_TRUST_STATE for a given urn" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustNameIF(urn, 400, invalidTrustJson)

      val result = controller.getTrustName(urn.value)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(
        Left(
          InvalidTrust(
            "INVALID_TRUST_STATE",
            "The remote endpoint has indicated that the Trust/Estate is Closed and playback is not possible.")))
    }

    "handles 404 response from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustNameIF(urn, 404, notFoundJson)

      val result = controller.getTrustName(urn.value)(request)
      status(result) shouldBe 200
      contentAsJson(result).as[TrustResponse] shouldBe TrustResponse(
        Left(InvalidTrust("RESOURCE_NOT_FOUND", "The remote endpoint has indicated that the trust is not found.")))
    }
  }
}
