package uk.gov.hmrc.agentclientauthorisation.controllers
import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientInvitationsControllerISpec extends BaseISpec {


  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat = app.injector.instanceOf[Materializer]

  "GET /clients/:service/:taxIdentifier/invitations/received" should {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")

    "return 200 with Json Body of ITSA Invitations" in new StubsSuccessfulScenariosTest(personal, Service.MtdIt) {
      val result = await(controller.getInvitations("MTDITID", mtdItId.value, None)(request))

      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
    }

    "return 200 with Json Body of NI Invitations" in new StubsSuccessfulScenariosTest(personal, Service.PersonalIncomeRecord) {
      val result = await(controller.getInvitations("NI", nino.value, None)(request))

      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
    }

    "return 200 with Json Body of VAT Invitations" in new StubsSuccessfulScenariosTest(personal, Service.Vat) {
      val result = await(controller.getInvitations("VRN", vrn.value, None)(request))

      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
    }

    "return 200 with Json Body of Trust Invitations" in new StubsSuccessfulScenariosTest(personal, Service.Trust) {
      val result = await(controller.getInvitations("UTR", utr.value, None)(request))

      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
    }
  }

  class StubsSuccessfulScenariosTest(clientType: Option[String], service: Service,
                                     mtdItId: MtdItId = mtdItId,
                                     vrn: Vrn = vrn,
                                     nino: Nino = nino,
                                     utr: Utr = utr) {

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


