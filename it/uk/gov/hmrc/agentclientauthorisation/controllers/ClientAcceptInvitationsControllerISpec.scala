package uk.gov.hmrc.agentclientauthorisation.controllers
import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.mvc.{Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ClientAcceptInvitationsControllerISpec extends BaseISpec {

  val trustController: TrustClientInvitationsController = app.injector.instanceOf[TrustClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat = app.injector.instanceOf[Materializer]

  "PUT /clients/UTR/:utr/invitations/received/:invitationId/accept" should {
    val request = FakeRequest("PUT", "/clients/UTR/:utr/invitations/received/:invitationId/accept")

    "return 204" when {
      "successfully accepting an invitation" in new StubsSuccessfulScenariosTest(business, Service.Trust, "SAUTR") {

        val invitationId: InvitationId = createForScenario.invitationId

        val result: Future[Result] = trustController.acceptInvitation(utr, invitationId)(request)

        status(result) shouldBe 204
      }

      "user is authenticated through stride" in new StubsSuccessfulScenariosTest(business, Service.Trust, "SAUTR", true) {

        val invitationId: InvitationId = createForScenario.invitationId

        val result: Future[Result] = trustController.acceptInvitation(utr, invitationId)(request)

        status(result) shouldBe 204
      }
    }

    "return 404" when {
      "attempting to accept an invitation that does not exist" in {
        givenClientAll(mtdItId, vrn, nino, utr)

        val result: Future[Result] = trustController.acceptInvitation(utr, InvitationId("D123456789"))(request)

        status(result) shouldBe 404
      }
    }

    "return 403" when {
      "attempting to accept an invitation that is not for the current logged in client" in {
        givenClientAll(mtdItId, vrn, nino, utr)

        val result: Future[Result] = trustController.acceptInvitation(Utr("12345657689"), InvitationId("D123456789"))(request)

        status(result) shouldBe 403
      }
    }
  }

  class StubsSuccessfulScenariosTest(clientType: Option[String],
                                     service: Service,
                                     identifierKey: String,
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
      }
      case Service.PersonalIncomeRecord => {
        await(createInvitation(arn, nino, nino))
      }
      case Service.Vat => {
        await(createInvitation(arn, vrn, vrn))
      }
      case Service.Trust => {
        givenCreateRelationship(arn, service.id, identifierKey, utr)
        await(createInvitation(arn, utr, utr))
      }
    }

    def createInvitation(arn: Arn, clientId: ClientId, suppliedClientId: ClientId): Future[Invitation] =
      repository.create(arn, clientType, service, clientId, suppliedClientId, Some(dfe), DateTime.now(DateTimeZone.UTC),
        LocalDate.now().plusDays(14))
  }
}
