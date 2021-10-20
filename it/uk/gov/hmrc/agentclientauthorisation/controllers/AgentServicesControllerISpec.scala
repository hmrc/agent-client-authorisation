package uk.gov.hmrc.agentclientauthorisation.controllers

import org.scalatest.Suite
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatestplus.play.ServerProvider
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{SuspensionDetails, VatCustomerDetails, VatIndividual}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, PptRef, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

class AgentServicesControllerISpec extends BaseISpec {

  this: Suite with ServerProvider =>

  override lazy val appBuilder: GuiceApplicationBuilder = new GuiceApplicationBuilder()
    .configure(additionalConfiguration).configure("agent.cache.enabled" -> false)

  val url = s"http://localhost:$port/agent-client-authorisation"

  implicit override val patienceConfig = PatienceConfig(timeout = Span(10, Seconds), interval = Span(10, Millis))

  val tradingName = "My Trading Name"
  val customerDetails = VatCustomerDetails(
    Some("Organisation Name"),
    Some(VatIndividual(Some("Miss"), Some("First Name"), Some("Middle Name"), Some("Last Name"))),
    Some("Trading Name"))

  val req = FakeRequest()

  val wsClient: WSClient = app.injector.instanceOf[WSClient]


  def getCurrentAgencyName: WSResponse =
    wsClient
      .url(s"$url/agent/agency-name")
      .get()
      .futureValue

  def getCurrentAgencyEmail: WSResponse =
    wsClient
      .url(s"$url/agent/agency-email")
      .get()
      .futureValue

  def getCurrentAgencyDetails: WSResponse =
    wsClient
      .url(s"$url/agent/agency-details")
      .get()
      .futureValue

  def getCurrentSuspensionDetails: WSResponse =
    wsClient
    .url(s"$url/agent/suspension-details")
    .get()
    .futureValue

  def getAgencyNameFor(arn: Arn): WSResponse =
    wsClient
      .url(s"$url/client/agency-name/${arn.value}")
      .get()
      .futureValue

  def getAgencyEmailFor(arn: Arn): WSResponse =
    wsClient
      .url(s"$url/client/agency-email/${arn.value}")
      .get()
      .futureValue

  def getSuspensionDetailsFor(arn: Arn): WSResponse =
    wsClient
      .url(s"$url/client/suspension-details/${arn.value}")
      .get()
      .futureValue

  def getAgencyNameClientWithUtr(utr: Utr): WSResponse =
    wsClient
      .url(s"$url/client/agency-name/utr/${utr.value}")
      .get()
      .futureValue

  def getAgencyNames(arns: Seq[Arn]): WSResponse =
    wsClient
      .url(s"$url/client/agency-names")
      .post(Json.toJson(arns.map(_.value)))
      .futureValue

  def getUtrAgencyNames(utrs: Seq[Utr]): WSResponse =
    wsClient
      .url(s"$url/client/agency-names/utr")
      .post(Json.toJson(utrs.map(_.value)))
      .futureValue

  def getClientNino(mtdItId: MtdItId): WSResponse =
    wsClient
      .url(s"$url/client/mtdItId/${mtdItId.value}")
      .get()
      .futureValue

  def getClientMtdItId(nino: Nino): WSResponse =
    wsClient
      .url(s"$url/client/nino/${nino.value  }")
      .get()
      .futureValue

  def getUtrBusinessName(utr: Utr): WSResponse =
    wsClient
      .url(s"$url/client/business-name/utr/${utr.value}")
      .get()
      .futureValue

  def getUtrBusinessNames(utrs: Seq[Utr]): WSResponse =
    wsClient
      .url(s"$url/client/business-names/utr")
      .post(Json.toJson(utrs.map(_.value)))
      .futureValue

  def getTradingNameForNino(nino: Nino): WSResponse =
    wsClient
      .url(s"$url/client/trading-name/nino/${nino.value}")
      .get()
      .futureValue

  def getVatCustomerDetails(vrn: Vrn): WSResponse =
    wsClient
      .url(s"$url/client/vat-customer-details/vrn/${vrn.value}")
      .get()
      .futureValue

  def getPptCustomerName(pptRef: PptRef): WSResponse =
    wsClient
      .url(s"$url/client/ppt-customer-name/pptref/${pptRef.value}")
      .get()
      .futureValue

  "GET /agent/agency-name/utr" should {
    "return Ok and agencyName after successful response" in {
      isLoggedIn
      givenDESRespondsWithValidData(utr, "ACME")
      givenDESRespondsWithValidData(Utr("4000000009"), "Other name")

      val result = getUtrAgencyNames(Seq(utr, Utr("4000000009")))
      result.status shouldBe OK
      result.json shouldBe Json.arr(
        Json.obj("utr" -> utr.value, "agencyName"    -> "ACME"),
        Json.obj("utr" -> "4000000009", "agencyName" -> "Other name"))
    }

    "return OK when there is no name" in {
      isLoggedIn
      authorisedAsValidClient(req, mtdItId.value)
      givenDESRespondsWithoutValidData(utr)

      val result = getUtrAgencyNames(Seq(utr))
      result.status shouldBe OK
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDESReturnsError(utr, BAD_GATEWAY)

      val result = getUtrAgencyNames(Seq(utr))
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }

    "return BadRequest when receiving an invalid Utr" in {
      isLoggedIn
      authorisedAsValidClient(req, mtdItId.value)

      val result = getUtrAgencyNames(Seq(Utr("invalidUtr")))
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Utr"
    }

    "return BadRequest when receiving an no utrs" in {
      isLoggedIn
      authorisedAsValidClient(req, mtdItId.value)

      val result = getUtrAgencyNames(Seq.empty[Utr])
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Utr"
    }
  }

  "GET /agent/agency-name" should {
    "return Ok and agencyName after successful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithValidData(arn, "ACME")

      val result = getCurrentAgencyName
      result.status shouldBe OK
      result.json shouldBe Json.obj("agencyName" -> "ACME")
    }

    "return NoContent after successful response but there is no agencyName" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithoutValidData(arn)

      val result = getCurrentAgencyName
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getCurrentAgencyName
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }
  }

  "GET /agent/agency-email" should {
    "return Ok and agencyEmail after successful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithValidData(arn, "ACME")

      val result = getCurrentAgencyEmail
      result.status shouldBe OK
      result.json shouldBe Json.obj("agencyEmail" -> "abc@xyz.com")
    }

    "return NoContent after successful response but there is no agencyEmail" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithoutValidData(arn)

      val result = getCurrentAgencyEmail
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getCurrentAgencyEmail
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }
  }

  "GET /agent/agency-details" should {
    "return Ok and agencyDetails after successful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithValidData(arn, "ACME")

      val result = getCurrentAgencyDetails
      result.status shouldBe OK
      result.json shouldBe Json.obj(
        "agencyEmail" -> "abc@xyz.com",
        "agencyName" -> "ACME",
        "agencyAddress" -> Json.obj(
          "addressLine1" -> "Matheson House",
          "addressLine2" -> "Grange Central",
          "addressLine3" -> "Town Centre",
          "addressLine4" -> "Telford",
          "postalCode" -> "TF3 4ER",
          "countryCode" -> "GB"
        ))
    }

    "return NoContent after successful response but there is no agencyDetails" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithoutValidData(arn)

      val result = getCurrentAgencyDetails
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getCurrentAgencyDetails
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }
  }

  "GET /agent/suspension-details" should {
    "return Ok and suspension details after successful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithValidData(arn, "ACME", SuspensionDetails(suspensionStatus = true, Some(Set("ITSA", "VATC"))))

      val result = getCurrentSuspensionDetails
      result.status shouldBe OK
      result.json shouldBe Json.toJson(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA", "VATC"))))
    }

    "return NoContent after successful response but there are no suspensionDetails" in {
      givenAuthorisedAsAgent(arn)
      givenDESRespondsWithoutValidData(arn)

      val result = getCurrentSuspensionDetails
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      givenAuthorisedAsAgent(arn)
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getCurrentSuspensionDetails
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }
  }

  "GET /client/agency-name/:arn" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenDESRespondsWithValidData(arn, "ACME")

      val result = getAgencyNameFor(arn)
      result.status shouldBe OK
      result.json shouldBe Json.obj("agencyName" -> "ACME")
    }

    "return NoContent after successful response but there is no name" in {
      isLoggedIn
      givenDESRespondsWithoutValidData(arn)

      val result = getAgencyNameFor(arn)
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getAgencyNameFor(arn)
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }

    "return BadRequest when receiving an invalid Arn" in {
      val result = getAgencyNameFor(Arn("InvalidArn[][][][][]["))
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Arn"
    }
  }

  "GET /client/agency-email/:arn" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenDESRespondsWithValidData(arn, "ACME")

      val result = getAgencyEmailFor(arn)
      result.status shouldBe OK
      result.json shouldBe Json.obj("agencyEmail" -> "abc@xyz.com")
    }

    "return NoContent after successful response but there is no email" in {
      isLoggedIn
      givenDESRespondsWithoutValidData(arn)

      val result = getAgencyEmailFor(arn)
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getAgencyEmailFor(arn)
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }

    "return BadRequest when receiving an invalid Arn" in {
      val result = getAgencyEmailFor(Arn("InvalidArn[][][][][]["))
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Arn"
    }
  }

  "GET /client/agent-suspension/:arn" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenDESRespondsWithValidData(arn, "ACME", SuspensionDetails(suspensionStatus = true, Some(Set("ITSA", "VATC"))))

      val result = getSuspensionDetailsFor(arn)
      result.status shouldBe OK
      result.json shouldBe Json.toJson(SuspensionDetails(suspensionStatus = true, Some(Set("ITSA", "VATC"))))
    }

    "return NoContent after successful response but there are no suspension details" in {
      isLoggedIn
      givenDESRespondsWithoutValidData(arn)

      val result = getSuspensionDetailsFor(arn)
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getSuspensionDetailsFor(arn)
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }

    "return BadRequest when receiving an invalid Arn" in {
      val result = getSuspensionDetailsFor(Arn("InvalidArn[][][][][]["))
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Arn"
    }
  }

  "GET /client/agency-name/utr/:utr" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenDESRespondsWithValidData(utr, "ACME")

      val result = getAgencyNameClientWithUtr(utr)
      result.status shouldBe OK
      result.json shouldBe Json.obj("agencyName" -> "ACME")
    }

    "return NoContent after successful response but there is no name" in {
      isLoggedIn
      givenDESRespondsWithoutValidData(utr)

      val result = getAgencyNameClientWithUtr(utr)
      result.status shouldBe NO_CONTENT
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDESReturnsError(utr, BAD_GATEWAY)

      val result = getAgencyNameClientWithUtr(utr)
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }

    "return BadRequest when receiving an invalid utr" in {
      isLoggedIn

      val result = getAgencyNameClientWithUtr(Utr("InvalidUtr[][][][][]["))
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Utr"
    }
  }

  "POST /client/agency-names" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenDESRespondsWithValidData(arn, "ACME")

      val result = getAgencyNames(Seq(arn))
      result.status shouldBe OK
      result.json shouldBe Json.arr(Json.obj("arn" -> arn.value, "agencyName" -> "ACME"))
    }

    "return Ok when one agent is ok but the other is terminated" in {
      isLoggedIn
      givenDESRespondsWithValidData(arn, "ACME")
      givenDESReturnsError(arn3, 500, terminatedResponseBody)

      val result = getAgencyNames(Seq(arn, arn3))
      result.status shouldBe OK
      result.json shouldBe Json.arr(Json.obj("arn" -> arn.value, "agencyName" -> "ACME"))
    }

    "return OK when there is no name" in {
      isLoggedIn
      givenDESRespondsWithoutValidData(arn)

      val result = getAgencyNames(Seq(arn))
      result.status shouldBe OK
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDESReturnsError(arn, BAD_GATEWAY)

      val result = getAgencyNames(Seq(arn))
      result.status shouldBe BAD_GATEWAY
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
    }

    "return BadRequest when receiving an invalid Arn" in {
      isLoggedIn
      val result = getAgencyNames(Seq(Arn("blah")))
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Arns: (blah)"
    }

    "return BadRequest when receiving an no arns" in {
      isLoggedIn
      val result = getAgencyNames(Seq.empty[Arn])
      result.status shouldBe BAD_REQUEST
      (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
      (result.json \ "message").get.as[String] shouldBe "Invalid Arns: ()"
    }
  }

  "GET /client/mtdItId/:mtdItId" should {
    "return Ok and a valid nino in the response" in {
      isLoggedIn
      givenNinoIsKnownFor(mtdItId, nino)

      val result = getClientNino(mtdItId)
      result.status shouldBe OK
      (result.json \ "nino").get.as[String] shouldBe nino.nino
    }

    "return NotFound and if there is no nino available for mtdItId" in {
      isLoggedIn
      givenNinoIsUnknownFor(mtdItId)

      val result = getClientNino(mtdItId)
      result.status shouldBe NOT_FOUND
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDesReturnsServiceUnavailable()

      val result = getClientNino(mtdItId)
      result.status shouldBe SERVICE_UNAVAILABLE
      (result.json \ "statusCode").get.as[Int] shouldBe SERVICE_UNAVAILABLE
    }
  }

  "GET /client/nino/:nino" should {
    "return Ok and a valid mtdItId in the response" in {
      isLoggedIn
      givenMtdItIdIsKnownFor(nino,mtdItId)

      val result = getClientMtdItId(nino)
      result.status shouldBe OK
      (result.json \ "mtdItId").get.as[String] shouldBe mtdItId.value
    }

    "return NotFound and if there is no nino mtdItId for nino" in {
      isLoggedIn
      givenMtdItIdIsUnknownFor(nino)

      val result = getClientMtdItId(nino)
      result.status shouldBe NOT_FOUND
    }

    "return an exception when there is an unsuccessful response" in {
      isLoggedIn
      givenDesReturnsServiceUnavailable()

      val result = getClientMtdItId(nino)
      result.status shouldBe SERVICE_UNAVAILABLE
      (result.json \ "statusCode").get.as[Int] shouldBe SERVICE_UNAVAILABLE
    }
  }

  "GET /client/business-name/utr/:utr" should {
    behave like testBusinessNameFor("CT AGENT 165", false)
    behave like testBusinessNameFor("First Name QM Last Name QM", true)

    def testBusinessNameFor(businessName: String, isIndividual: Boolean) = {
      s"return Ok and data after successful response for isIndividual = $isIndividual" in {
        isLoggedIn
        givenDESRespondsWithRegistrationData(utr, isIndividual)

        val result = getUtrBusinessName(utr)
        result.status shouldBe OK
        result.json shouldBe Json.obj("businessName" -> businessName)
      }

      s"return OK after successful response but there is no name for isIndividual = $isIndividual" in {
        isLoggedIn
        givenDESRespondsWithoutRegistrationData(utr)

        val result = getUtrBusinessName(utr)
        result.status shouldBe OK
        result.json shouldBe Json.obj("businessName" -> "")
      }

      s"return an exception when there is an unsuccessful response for isIndividual = $isIndividual" in {
        isLoggedIn
        givenDESReturnsErrorForRegistration(utr, BAD_GATEWAY)

        val result = getUtrBusinessName(utr)
        result.status shouldBe BAD_GATEWAY
        (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
      }

      s"return BadRequest when receiving an invalid utr for isIndividual = $isIndividual" in {
        isLoggedIn

        val result = getUtrBusinessName(Utr("InvalidUtr[][][][][]["))
        result.status shouldBe BAD_REQUEST
        (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
        (result.json \ "message").get.as[String] shouldBe "Invalid Utr"
      }

      s"return 404 when businessRecord notFound for isIndividual = $isIndividual" in {
        isLoggedIn
        givenDESReturnsErrorForRegistration(
          Utr("4000000009"),
          404,
          """{"statusCode":"404","message":"No business record was matched for the specified UTR"}""")

        val result = getUtrBusinessName(Utr("4000000009"))
        result.status shouldBe NOT_FOUND
        (result.json \ "statusCode").get.as[Int] shouldBe NOT_FOUND
      }
    }
  }

  "POST /client/business-names/utr" should {
    behave like testGetBusinessNames("CT AGENT 165", false)
    behave like testGetBusinessNames("First Name QM Last Name QM", true)

    def testGetBusinessNames(businessName: String, isIndividual: Boolean): Unit = {
      s"return Ok and data after successful response for isIndividual=$isIndividual" in {
        isLoggedIn
        givenDESRespondsWithRegistrationData(utr, isIndividual)

        val result = getUtrBusinessNames(Seq(utr))
        result.status shouldBe OK
        result.json shouldBe Json.arr(Json.obj("utr" -> utr.value, "businessName" -> businessName))
      }

      s"return OK after successful response but there is no name for isIndividual=$isIndividual" in {
        isLoggedIn
        givenDESRespondsWithoutRegistrationData(utr)

        val result = getUtrBusinessNames(Seq(utr))
        result.status shouldBe OK
        result.json shouldBe Json.arr(Json.obj("utr" -> utr.value, "businessName" -> ""))
      }

      s"Ok when multiple UTRs $isIndividual" in {
        isLoggedIn
        givenDESRespondsWithRegistrationData(utr, isIndividual)
        givenDESRespondsWithRegistrationData(Utr("4000000009"), !isIndividual)

        val result = getUtrBusinessNames(Seq(utr, Utr("4000000009")))
        result.status shouldBe OK
      }

      s"return ok but one of the agents discovered is terminated for $isIndividual" in {
        isLoggedIn
        givenDESRespondsWithRegistrationData(utr, isIndividual)
        givenDESReturnsErrorForRegistration(Utr("4000000009"), INTERNAL_SERVER_ERROR, terminatedResponseBody)

        getUtrBusinessNames(Seq(utr, Utr("4000000009"))).status shouldBe INTERNAL_SERVER_ERROR
      }

      s"Error thrown by Des on one of the UTRs $isIndividual" in {
        isLoggedIn
        givenDESRespondsWithRegistrationData(utr, isIndividual)
        givenDESReturnsErrorForRegistration(Utr("4000000009"), BAD_GATEWAY)

        getUtrBusinessNames(Seq(utr, Utr("4000000009"))).status shouldBe 502
      }

      s"return an exception when there is an unsuccessful response for isIndividual=$isIndividual" in {
        isLoggedIn
        givenDESReturnsErrorForRegistration(utr, BAD_GATEWAY)

        val result = getUtrBusinessNames(Seq(utr))
        result.status shouldBe BAD_GATEWAY
        (result.json \ "statusCode").get.as[Int] shouldBe BAD_GATEWAY
      }

      s"return BadRequest when receiving an invalid utr for isIndividual=$isIndividual" in {
        isLoggedIn

        val result = getUtrBusinessNames(Seq(Utr("InvalidUtr[][][][][][")))
        result.status shouldBe BAD_REQUEST
        (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
        (result.json \ "message").get.as[String] shouldBe "Invalid Utr"
      }

      s"return BadRequest when receiving an no utrs for isIndividual=$isIndividual" in {
        isLoggedIn

        val result = getUtrBusinessNames(Seq.empty[Utr])
        result.status shouldBe BAD_REQUEST
        (result.json \ "statusCode").get.as[Int] shouldBe BAD_REQUEST
        (result.json \ "message").get.as[String] shouldBe "Invalid Utr"
      }
    }

  }

  "GET /client/trading-name/nino/:nino" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenTradingNameIsKnownFor(nino, tradingName)

      val result = getTradingNameForNino(nino)
      result.status shouldBe OK

      (result.json \ "tradingName").get.as[String] shouldBe tradingName
    }

    "return not found when there is no trading name for the individual in ETMP and no name found in Citizen Details" in {
      isLoggedIn
      givenNoTradingNameFor(nino)
      givenCitizenDetailsReturnsResponseFor(nino.value, 404)

      val result = getTradingNameForNino(nino)
      result.status shouldBe NOT_FOUND
    }

    "return 200 when there is no trading name for the individual in ETMP but Citizen details found" in {
      isLoggedIn
      givenNoTradingNameFor(nino)
      givenCitizenDetailsAreKnownFor(nino.value, "any")

      val result = getTradingNameForNino(nino)
      result.status shouldBe OK

      result.json shouldBe Json.parse(s"""{"tradingName": "John Smith"}""")
    }
  }

  "GET /client/vat-customer-details/vrn/:vrn" should {
    "return Ok and data after successful response" in {
      isLoggedIn
      givenCustomerDetailsKnownFor(vrn)

      val result = getVatCustomerDetails(vrn)
      result.status shouldBe OK
      (result.json \ "individual" \ "firstName").get.as[String] shouldBe "Ronald"
      (result.json \ "individual" \ "middleName").get.as[String] shouldBe "A"
      (result.json \ "individual" \ "lastName").get.as[String] shouldBe "McDonald"
      (result.json \ "individual" \ "title").get.as[String] shouldBe "Mr"
      (result.json \ "tradingName").get.as[String] shouldBe "MCD"
      (result.json \ "organisationName").get.as[String] shouldBe "McDonalds CORP"
    }

    "return Ok when there is no individual information for the given vrn" in {
      isLoggedIn
      givenCustomerDetailsWithoutIndividual(vrn)

      val result = getVatCustomerDetails(vrn)
      result.status shouldBe OK
      (result.json \ "tradingName").get.as[String] shouldBe "MCD"
      (result.json \ "organisationName").get.as[String] shouldBe "McDonalds CORP"
    }

    "return Ok when there is no organisation name for the given vrn" in {
      isLoggedIn
      givenCustomerDetailsWithoutOrganisation(vrn)

      val result = getVatCustomerDetails(vrn)
      result.status shouldBe OK
      (result.json \ "individual" \ "firstName").get.as[String] shouldBe "Ronald"
      (result.json \ "individual" \ "middleName").get.as[String] shouldBe "A"
      (result.json \ "individual" \ "lastName").get.as[String] shouldBe "McDonald"
      (result.json \ "individual" \ "title").get.as[String] shouldBe "Mr"
    }

    "return not found when there are no customer details for the given vrn" in {
      isLoggedIn
      givenNoCustomerDetails(vrn)

      val result = getVatCustomerDetails(vrn)
      result.status shouldBe NOT_FOUND
    }
  }

  "GET /client/ppt-customer-name/pptref/:pptRef" should {
    "return OK for successful response" in {
      givenPptSubscription(pptRef, false, false, false)
      val result = getPptCustomerName(pptRef)
      result.status shouldBe 200
      (result.json \ "customerName").as[String] shouldBe "Fagan Ltd"
    }
  }
}
