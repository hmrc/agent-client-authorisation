package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.mvc.{Action, AnyContent}
import play.api.test.FakeRequest
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global


class EmailStatusInvitationControllerISpec extends BaseISpec {

  val controller = app.injector.instanceOf[EmailStatusInvitationController]

  //TODO Add Stubs
  "EmailStatusInvitationController" should {

    val request = FakeRequest("GET", "/known-facts/individuals/nino/:nino/sa/postcode/:postcode")

    "return 202 for sending agent an email that their client has accepted their invitation/s" in {
      val result = controller.sendEmail(request)

      status(result) shouldBe 202
    }

    "throw exception for malformed json" in {
      intercept[Exception] {
        await(controller.sendEmail())
      }
    }
  }

}
