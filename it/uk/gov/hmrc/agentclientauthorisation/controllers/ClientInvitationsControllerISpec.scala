package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentclientauthorisation.support.{RelationshipStubs, TestHalResponseInvitations}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientInvitationsControllerISpec extends BaseISpec with RelationshipStubs {

  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat = app.injector.instanceOf[Materializer]

  case class TestClient(
                         clientType: Option[String],
                         service: Service,
                         urlIdentifier: String,
                         clientId: TaxIdentifier,
                         suppliedClientId: TaxIdentifier)

  val itsaClient = TestClient(personal, MtdIt, "MTDITID", mtdItId, nino)
  val irvClient = TestClient(personal, PersonalIncomeRecord, "NI", nino, nino)
  val vatClient = TestClient(personal, Vat, "VRN", vrn, vrn)
  val trustClient = TestClient(business, Trust, "UTR", utr, utr)
  val cgtClient = TestClient(business, Service.CapitalGains, "CGTPDRef", cgtRef, cgtRef)

  val clients = List(itsaClient, irvClient, vatClient, trustClient)

  val clientsForGet: List[TestClient] = clients ++ List(cgtClient)

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
      givenClientAll(mtdItId, vrn, nino, utr, cgtRef)
  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/accept" should {
    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/accept")

    "accept invitation as expected" in new LoggedinUser(false) {
      clients.foreach { client =>
        givenCreateRelationship(arn, client.service.id, if(client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
        anAfiRelationshipIsCreatedWith(arn, client.clientId)

        val invitation = await(createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
        val result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
        status(result) shouldBe 204
      }
    }

    "return bad_request for invalid clientType and clientId combination" in new LoggedinUser(false) {
      clients.foreach { client =>
        val invalidClient = client.copy(urlIdentifier = client.urlIdentifier.toLowerCase)
        val result = await(controller.acceptInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, InvitationId("D123456789"))(request))
        status(result) shouldBe 400
      }
    }
  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/reject" should {
    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/reject")

    "reject invitation as expected" in new LoggedinUser(false) {
      clients.foreach { client =>
        givenCreateRelationship(arn, client.service.id, if(client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
        anAfiRelationshipIsCreatedWith(arn, client.clientId)

        val invitation = await(createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
        val result = await(controller.rejectInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
        status(result) shouldBe 204
      }
    }

    "return bad_request for invalid clientType and clientId combination" in new LoggedinUser(false) {
      clients.foreach { client =>
        val invalidClient = client.copy(urlIdentifier = client.urlIdentifier.toLowerCase)
        val result = await(controller.rejectInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, InvitationId("D123456789"))(request))
        status(result) shouldBe 400
      }
    }
  }

  "GET /clients/:clientIdType/:clientId/invitations/received/:invitationId" should {
    val request = FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/received/:invitationId")

    "return invitation as expected" in new LoggedinUser(false) {

      clientsForGet.foreach { client =>
        val invitation = await(createInvitation(client.clientType, client.service, arn, client.clientId, client.suppliedClientId))
        val result = await(controller.getInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
        status(result) shouldBe 200
      }
    }

    "return bad_request for invalid clientType and clientId combination" in new LoggedinUser(false) {
      clientsForGet.foreach { client =>
        val invalidClient = client.copy(urlIdentifier = client.urlIdentifier.toLowerCase)
        val result = await(controller.getInvitation(invalidClient.urlIdentifier, client.clientId.value, InvitationId("D123456789"))(request))
        status(result) shouldBe 400
      }
    }
  }

  "GET /clients/:service/:taxIdentifier/invitations/received" should {
    clientsForGet.foreach { client =>
      runGetAllInvitationsScenario(client, true)
      runGetAllInvitationsScenario(client, false)
    }
  }

  private def runGetAllInvitationsScenario(testClient: TestClient, forStride: Boolean) = {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")
    s"return 200 for get all ${testClient.service.id} invitations for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))
      await(createInvitation(testClient.clientType, testClient.service, arn2, testClient.clientId, testClient.suppliedClientId))

      val result: Result = await(controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request))

      status(result) shouldBe 200

      val json = (jsonBodyOf(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    s"return 200 for getting no ${testClient.service.id} invitations for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val result = await(controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request))

      status(result) shouldBe 200

      val json = (jsonBodyOf(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 0
    }
  }

  val determineServiceUrl: TaxIdentifier => String = {
    case MtdItId(_) => "MTDITID"
    case Nino(_)    => "NI"
    case Vrn(_)     => "VRN"
    case Utr(_)     => "SAUTR"
  }

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

      val result: Future[Result] = controller.acceptInvitation("UTR", utr.value, invitationId)(request)

      status(result) shouldBe 204
    }

    s"attempting to accept an invitation that does not exist for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val result: Future[Result] = controller.acceptInvitation("UTR", utr.value, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting to accept an invitation that is not for the client if logged in as ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = controller.acceptInvitation("UTR", utr2.value, invitationId)(request)

      status(result) shouldBe 403
    }
  }

  "PUT /clients/UTR/:utr/invitations/received/:invitationId/reject" should {
    runRejectInvitationScenario(trustClient, true)
    runRejectInvitationScenario(trustClient, false)
  }

  def runRejectInvitationScenario(testClient: TestClient, forStride: Boolean) = {
    val request = FakeRequest("PUT", "/clients/UTR/:utr/invitations/received/:invitationId/reject")
    s"successfully rejecting an invitation for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = controller.rejectInvitation("UTR", utr.value, invitationId)(request)

      status(result) shouldBe 204
    }

    s"attempting to reject an invitation that does not exist for current logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val result: Future[Result] = controller.rejectInvitation("UTR", utr.value, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting to reject an invitation that is not for the client for logged in ${if(forStride) "stride" else "client"}" in new LoggedinUser(forStride) {
      val invitation: Invitation = await(createInvitation(testClient.clientType, testClient.service, arn, testClient.clientId, testClient.suppliedClientId))

      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = controller.rejectInvitation("UTR", utr2.value, invitationId)(request)

      status(result) shouldBe 403
    }
  }
}
