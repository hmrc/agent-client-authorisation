package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.{TrustAddress, TrustDetails, TrustDetailsResponse}
import uk.gov.hmrc.agentmtdidentifiers.model.Utr
import uk.gov.hmrc.http.BadRequestException

class AgentGetTrustDetailsISpec extends BaseISpec {

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  "GET /known-facts/organisations/trust/:utr" should {

    val utr = Utr("2134514321")

    val trustDetailsResponse =
      TrustDetailsResponse(
        TrustDetails(
          utr.value,
          "Nelson James Trust",
          TrustAddress("10 Enderson Road", "Cheapside", Some("Riverside"), Some("Boston"), Some("SN8 4DD"), "GB"),
          "TERS"))

    val trustDetailsSuccessResponseJson = Json.toJson(trustDetailsResponse).toString()

    val errorJson =
      """{"code": "INVALID_UTR","reason": "Submission has not passed validation. Invalid parameter UTR."}"""

    val request = FakeRequest("GET", s"/known-facts/organisations/trust/${utr.value}")

    "return success response for a given utr" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustDetails(utr, 200, trustDetailsSuccessResponseJson)

      val result = controller.knownFactForTrust(utr)(request)
      status(result) shouldBe 200

      jsonBodyOf(result).as[TrustDetailsResponse] shouldBe trustDetailsResponse

    }

    "return success response if trust known-facts are NOT found for a given utr" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustDetails(utr, 404, errorJson)

      val result = controller.knownFactForTrust(utr)(request)
      status(result) shouldBe 200
    }

    "handles 400 response from DES" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)
      getTrustDetails(utr, 400, errorJson)

      assertThrows[BadRequestException] { await(controller.knownFactForTrust(utr)(request)) }
    }
  }

}
