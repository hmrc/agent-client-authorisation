package uk.gov.hmrc.agentclientauthorisation.controllers
import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientGetInvitationsByIdentifierControllerISpec extends BaseISpec {


  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat = app.injector.instanceOf[Materializer]

  "GET /clients/:service/:taxIdentifier/invitations/received" should {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")

    "return 200" when {
      "found ITSA Invitations" in new StubsSuccessfulScenariosTest(personal, Service.MtdIt) {
        val result = await(controller.getInvitations("MTDITID", mtdItId.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
      }

      "found NI Invitations" in new StubsSuccessfulScenariosTest(personal, Service.PersonalIncomeRecord) {
        val result = await(controller.getInvitations("NI", nino.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
      }

      "found VAT Invitations" in new StubsSuccessfulScenariosTest(personal, Service.Vat) {
        val result = await(controller.getInvitations("VRN", vrn.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
      }

      "found Trust Invitations" in new StubsSuccessfulScenariosTest(personal, Service.Trust) {
        val result = await(controller.getInvitations("UTR", utr.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
      }

      "user is authenticated through stride" in new StubsSuccessfulScenariosTest(business, Service.Trust, true) {
        val result = await(controller.getInvitations("UTR", utr.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
      }
    }

    "return 200 still" when {
      "no ITSA Invitations were found" in {
        givenClientAll(mtdItId, vrn, nino, utr)
        val result: Result = await(controller.getInvitations("NI", nino.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").asOpt[String] shouldBe None
      }

      "no NI Invitations were found" in {
        givenClientAll(mtdItId, vrn, nino, utr)
        val result: Result = await(controller.getInvitations("NI", nino.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").asOpt[String] shouldBe None
      }

      "not VAT Invitations were found" in {
        givenClientAll(mtdItId, vrn, nino, utr)
        val result: Result = await(controller.getInvitations("VRN", vrn.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").asOpt[String] shouldBe None
      }

      "no UTR Invitations were found" in {
        givenClientAll(mtdItId, vrn, nino, utr)
        val result: Result = await(controller.getInvitations("UTR", utr.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").asOpt[String] shouldBe None
      }

      "user is authenticated through stride" in {
        givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
        val result: Result = await(controller.getInvitations("UTR", utr.value, None)(request))

        status(result) shouldBe 200

        ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").asOpt[String] shouldBe None
      }
    }
  }

  class StubsSuccessfulScenariosTest(clientType: Option[String],
                                     service: Service,
                                     forStride: Boolean = false,
                                     mtdItId: MtdItId = mtdItId,
                                     vrn: Vrn = vrn,
                                     nino: Nino = nino,
                                     utr: Utr = utr) {

    if(forStride)
      givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
    else
      givenClientAll(mtdItId, vrn, nino, utr)

    val createForScenario: Invitation = service match {
      case Service.MtdIt => {
        await(createInvitation(arn, mtdItId, nino))
        await(createInvitation(arn2, mtdItId, nino))
      }
      case Service.PersonalIncomeRecord => {
        await(createInvitation(arn, nino, nino))
        await(createInvitation(arn2, nino, nino))
      }
      case Service.Vat => {
        await(createInvitation(arn, vrn, vrn))
        await(createInvitation(arn2, vrn, vrn))
      }
      case Service.Trust => {
        await(createInvitation(arn, utr, utr))
        await(createInvitation(arn2, utr, utr))
      }
    }

    def createInvitation(arn: Arn, clientId: ClientId, suppliedClientId: ClientId): Future[Invitation] =
      repository.create(arn, clientType, service, clientId, suppliedClientId, None, DateTime.now(DateTimeZone.UTC),
        LocalDate.now().plusDays(14))
  }

}


