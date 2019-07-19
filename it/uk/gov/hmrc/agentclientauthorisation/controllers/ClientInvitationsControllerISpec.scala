package uk.gov.hmrc.agentclientauthorisation.controllers
import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientInvitationsControllerISpec extends BaseISpec {

  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat = app.injector.instanceOf[Materializer]

  case class TestClient(
                         clientType: Option[String],
                         service: Service,
                         urlIdentifier: String,
                         clientId: TaxIdentifier,
                         suppliedClientId: TaxIdentifier)

  val itsaClient = TestClient(personal, Service.MtdIt, "MTDITID", mtdItId, nino)
  val irvClient = TestClient(personal, Service.PersonalIncomeRecord, "NI", nino, nino)
  val vatClient = TestClient(personal, Service.Vat, "VRN", vrn, vrn)
  val trustClient = TestClient(business, Service.Trust, "UTR", utr, utr)

  val list = List(itsaClient, irvClient, vatClient, trustClient)

  def createInvitation(clientType: Option[String],
                       service: Service,
                       arn: Arn,
                       clientId: ClientId,
                       suppliedClientId: ClientId): Future[Invitation] = {
    repository.create(
      arn,
      clientType,
      service,
      clientId,
      suppliedClientId,
      Some(dfe),
      DateTime.now(DateTimeZone.UTC),
      LocalDate.now().plusDays(14))
  }

  class LoggedinUser(forStride: Boolean) {
    if(forStride)
      givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
    else
      givenClientAll(mtdItId, vrn, nino, utr)
  }

  //TODO update when refactor in the future
  "GET /clients/:service/:identifier/invitations/received/:invitationId" should {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received/:invitationId")

    "return 501" in {
      val result = await(controller.getInvitation(trustClient.urlIdentifier, trustClient.clientId.value, InvitationId("D123456789"))(request))

      status(result) shouldBe 501
    }
  }

  "GET /clients/:service/:taxIdentifier/invitations/received" should {
    list.foreach { client =>
      runGetAllInvitationsScenario(client, true)
      runGetAllInvitationsScenario(client, false)
    }
  }

  private def runGetAllInvitationsScenario(testClient: TestClient, forStride: Boolean) = {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")
    s"return 200 for get all ${testClient.service.id} invitations for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))
      await(createInvitation(testClient.clientType, testClient.service, arn2, testClient.clientId, testClient.suppliedClientId))

      val result = await(controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request))

      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").as[String] shouldBe arn2.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "clientId").as[String] shouldBe testClient.clientId.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").as[String] shouldBe arn.value
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "clientId").as[String] shouldBe testClient.clientId.value
    }

    s"return 200 for getting no ${testClient.service.id} invitations for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val result = await(controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request))

      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "arn").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "clientId").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "arn").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(1) \ "clientId").asOpt[String] shouldBe None
    }
  }

  //TODO Replace this With ClientInvitationsController
  val trustController: TrustClientInvitationsController = app.injector.instanceOf[TrustClientInvitationsController]

  val determineServiceUrl: TaxIdentifier => String = {
    case MtdItId(_) => "MTDITID"
    case Nino(_)    => "NI"
    case Vrn(_)     => "VRN"
    case Utr(_)     => "SAUTR"
  }


  //TODO Rework this with ForEach
  "PUT /clients/UTR/:utr/invitations/received/:invitationId/accept" should {
    runAcceptInvitationScenario(trustClient, true)
    runAcceptInvitationScenario(trustClient, false)
  }

  def runAcceptInvitationScenario(testClient: TestClient, forStride: Boolean) = {
    val request = FakeRequest("PUT", "/clients/UTR/:utr/invitations/received/:invitationId/accept")

    s"return 204 for accepting an Invitation for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      givenCreateRelationship(arn, testClient.service.id, determineServiceUrl(testClient.clientId), testClient.clientId)
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = trustController.acceptInvitation(utr, invitationId)(request)

      status(result) shouldBe 204
    }

    s"attempting to accept an invitation that does not exist for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val result: Future[Result] = trustController.acceptInvitation(utr, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting to accept an invitation that is not for the client if logged in as ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = trustController.acceptInvitation(utr2, invitationId)(request)

      status(result) shouldBe 403
    }
  }

  //TODO Rework this with ForEach
  "PUT /clients/UTR/:utr/invitations/received/:invitationId/reject" should {
    runRejectInvitationScenario(trustClient, true)
    runRejectInvitationScenario(trustClient, false)
  }

  def runRejectInvitationScenario(testClient: TestClient, forStride: Boolean) = {
    val request = FakeRequest("PUT", "/clients/UTR/:utr/invitations/received/:invitationId/reject")
    s"successfully rejecting an invitation for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = trustController.rejectInvitation(utr, invitationId)(request)

      status(result) shouldBe 204
    }

    s"attempting to reject an invitation that does not exist for current logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val result: Future[Result] = trustController.rejectInvitation(utr, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting to reject an invitation that is not for the client for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = trustController.rejectInvitation(utr2, invitationId)(request)

      status(result) shouldBe 403
    }
  }
}
