package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.libs.json.Json
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation


class EmailControllerISpec extends BaseISpec {

  val controller = app.injector.instanceOf[EmailController]
  protected implicit val materializer = app.materializer

  //TODO Add Stubs
  "EmailController" should {

    val request = FakeRequest("POST", "/email")

    "return 202 for sending agent an email that their client has accepted their invitation/s" in {
      val emailInformation = EmailInformation(Seq("agent@email.com"), "accept-email-template", Map("clientName" -> "Mr client", "service" -> "VAT"))
      givenEmailSent(emailInformation)
      val result = controller.sendEmail(request.withJsonBody(Json.toJson(emailInformation)))

      status(result) shouldBe 202
    }
    "throw an exception if the email service returns an exception" in {
      val emailInformation = EmailInformation(Seq("agent@email.com"), "accept-email-template", Map("clientName" -> "Mr client", "service" -> "VAT"))
      givenEmailReturns500
      intercept[Exception] {
        await(controller.sendEmail(request.withJsonBody(Json.toJson(emailInformation))))
      }
    }
    "return a bad request if the json payload is invalid" in {
      val result = await(controller.sendEmail(request.withJsonBody(Json.parse("""{"foo": "bar"}"""))))

      status(result) shouldBe 400
      bodyOf(result) startsWith "Invalid payload:"
    }
    "return a bad request if there is no json" in {
      val result = await(controller.sendEmail(request))

      status(result) shouldBe 400
      bodyOf(result) startsWith "could not parse body due to: "
    }
  }

}
