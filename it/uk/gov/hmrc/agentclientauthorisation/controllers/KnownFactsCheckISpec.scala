package uk.gov.hmrc.agentclientauthorisation.controllers
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, NiExemptionRegistrationStub, Resource}
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

class KnownFactsCheckISpec extends UnitSpec with MongoAppAndStubs with AuthStubs with NiExemptionRegistrationStub {

  lazy val controller = app.injector.instanceOf(classOf[AgencyInvitationsController])

  val arn = "TARN0000001"
  val utr = "9246624558"
  val eori = "AQ886940109600"
  val postcode = "BN114AW"
  val name = "My Big Business"

  "GET /known-facts/ni/utr/:utr/postcode/:postcode" should {

    "return 200 and NiBusinessCheckResult when postcode matches and they are enrolled" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      givenNiBusinessExists(utr, name, Some(eori))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/known-facts/ni/utr/$utr/postcode/$postcode", port).get

      response.status shouldBe 200

      (response.json \ "postcodeMatches").as[Boolean] shouldBe true
      (response.json \ "eori").as[String] shouldBe eori
      (response.json \ "businessName").as[String] shouldBe name

    }

    "return 200 and NiBusinessCheckResult when postcode matches and they are NOT enrolled" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      givenNiBusinessExists(utr, name, None)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/known-facts/ni/utr/$utr/postcode/$postcode", port).get

      response.status shouldBe 200

      (response.json \ "postcodeMatches").as[Boolean] shouldBe true
      (response.json \ "eori").asOpt[String] shouldBe None
      (response.json \ "businessName").as[String] shouldBe name
    }

    "return 200 and NiBusinessCheckResult when postcode does not matches" in {

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      givenNiBusinessNotExists(utr)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/known-facts/ni/utr/$utr/postcode/$postcode", port).get

      response.status shouldBe 200

      (response.json \ "postcodeMatches").as[Boolean] shouldBe false
      (response.json \ "eori").asOpt[String] shouldBe None
      (response.json \ "businessName").asOpt[String] shouldBe None
    }

  }

}
