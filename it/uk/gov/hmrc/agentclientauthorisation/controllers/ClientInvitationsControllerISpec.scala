package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientInvitationsControllerISpec extends BaseISpec with RelationshipStubs with EmailStub {

  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat = app.injector.instanceOf[Materializer]

  def createInvitation[T<:TaxIdentifier](arn: Arn,
                       testClient: TestClient[T],
                       hasEmail: Boolean = true): Future[Invitation] = {
    repository.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if(hasEmail) Some(dfe(testClient.clientName)) else None,
      DateTime.now(DateTimeZone.UTC),
      LocalDate.now().plusDays(14))
  }

  def createEmailInfo(dfe: DetailsForEmail,
                      templateId: String,
                      service: Service): EmailInformation = {

    val serviceText = service match {
      case MtdIt => "send their Income Tax updates through software."
      case PersonalIncomeRecord => "view their PAYE income record."
      case Vat => "submit their VAT returns through software."
      case Trust => "maintain a trust or an estate."
      case CapitalGains => "manage their Capital Gains Tax on UK property disposals."
    }

    EmailInformation(Seq(dfe.agencyEmail),
      templateId,
      Map("agencyName"   -> s"${dfe.agencyName}",
        "clientName"   -> s"${dfe.clientName}",
        "expiryPeriod" -> "14 days",
        "service"      -> s"$serviceText"))
  }

  class LoggedInUser(forStride: Boolean, forBusiness: Boolean = false) {
    if(forStride)
      givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
    else if(forBusiness)
      givenClientAllBusCgt(cgtRefBus)
    else
      givenClientAll(mtdItId, vrn, nino, utr, cgtRef)
  }

  trait AddEmailSupportStub {
    givenGetAgencyDetailsStub(arn)
    givenNinoForMtdItId(mtdItId, nino)
    givenTradingName(nino, "Trade Pears")
    givenCitizenDetailsAreKnownFor(nino.value, "19122019")
    givenClientDetailsForVat(vrn)
    val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""
    getTrustName(utr, response = trustNameJson)
    getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())
    getCgtSubscription(cgtRefBus, 200, Json.toJson(cgtSubscriptionBus).toString())
  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/accept" should {
    uiClients.foreach { client =>
      runAcceptInvitationsScenario(client, "UI",false)
    }

    strideSupportedClient.foreach { client =>
      runAcceptInvitationsScenario(client, "UI",true)
    }

    apiClients.foreach { client =>
      runAcceptInvitationsScenario(client,"API",false)
    }

    runAcceptInvitationsScenario(cgtClientBus, "UI", false, true)
    runAcceptInvitationsScenario(cgtClientBus, "UI", true, true)
  }

  def runAcceptInvitationsScenario[T<: TaxIdentifier](client: TestClient[T], journey: String, forStride: Boolean, forBusiness: Boolean = false) = {
    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/accept")
    val getResult = FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/:invitationId")

    s"accept via $journey ${client.urlIdentifier} ${client.service.id} invitation as expected for ${client.clientId.value} ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) with AddEmailSupportStub {
      givenCreateRelationship(arn, client.service.id, if(client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
      givenEmailSent(createEmailInfo(dfe(client.clientName), "client_accepted_authorisation_request", client.service))
      anAfiRelationshipIsCreatedWith(arn, client.clientId)

      val invitation: Invitation = await(createInvitation(arn, client))
      val result: Result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204
      val updatedInvitation: Result = await(controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Accepted))(getResult))
      val testInvitationOpt: Option[TestHalResponseInvitation] = (jsonBodyOf(updatedInvitation) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Accepted.toString)
    }

    s"return via $journey bad_request for invalid clientType and clientId: ${client.clientId.value} combination ${if(forStride) "stride" else "client"}" in new LoggedInUser(false, forBusiness) {
      val invalidClient: TestClient[T] = client.copy[T](urlIdentifier = client.urlIdentifier.toLowerCase)
      val result: Result = await(controller.acceptInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, InvitationId("D123456789"))(request))
      status(result) shouldBe 400
    }

    s"attempting via $journey to accept an ${client.service.id} invitation that does not exist for ${client.clientId.value} logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      val result: Future[Result] = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, InvitationId("D123456789"))(request))

      status(result) shouldBe 404
    }

    s"attempting via $journey to accept an ${client.service.id} invitation that is not for the client: ${client.clientId.value} if logged in as ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      val invitation: Invitation = await(createInvitation(arn, client))
      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = await(controller.acceptInvitation(client.urlIdentifier, client.wrongIdentifier.value, invitationId)(request))

      status(result) shouldBe 403

      val invitationResult: Result = await(controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Pending))(getResult))
      val testInvitationOpt: Option[TestHalResponseInvitation] = (jsonBodyOf(invitationResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Pending.toString)
    }
  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/reject" should {

    uiClients.foreach { client =>
      runRejectInvitationsScenario(client, "UI",false)
    }

    strideSupportedClient.foreach { client =>
      runRejectInvitationsScenario(client, "UI",true)
    }

    apiClients.foreach { client =>
      runRejectInvitationsScenario(client, "API",false)
    }

    runRejectInvitationsScenario(cgtClientBus, "UI", false, true)
    runRejectInvitationsScenario(cgtClientBus, "UI", true, true)
  }

  def runRejectInvitationsScenario[T<:TaxIdentifier](client: TestClient[T], journey: String, forStride: Boolean, forBussiness: Boolean = false) = {
    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/reject")
    val getResult = FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/:invitationId")

    s"reject via $journey ${client.urlIdentifier} ${client.service.id} invitation for ${client.clientId.value} as expected with ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBussiness) with AddEmailSupportStub {
        givenEmailSent(createEmailInfo(dfe(client.clientName), "client_rejected_authorisation_request", client.service))

      val invitation: Invitation = await(createInvitation(arn, client))
        val result = await(controller.rejectInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
        status(result) shouldBe 204
      val updatedInvitation: Result = await(controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Rejected))(getResult))
      val testInvitationOpt: Option[TestHalResponseInvitation] = (jsonBodyOf(updatedInvitation) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Rejected.toString)
    }

    s"return via $journey bad_request for invalid clientType and clientId: ${client.clientId.value} combination ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBussiness) {
      val invalidClient: TestClient[T] = client.copy[T](urlIdentifier = client.urlIdentifier.toLowerCase)
      val result: Result = await(controller.rejectInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, InvitationId("D123456789"))(request))
      status(result) shouldBe 400
    }

    s"attempting via $journey to reject an ${client.service.id} invitation that does not exist for ${client.clientId.value} to logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBussiness) {
      val result: Future[Result] = controller.rejectInvitation(client.urlIdentifier, client.clientId.value, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting via $journey to reject an ${client.service.id} invitation that is not for the client: ${client.clientId.value} if logged in as ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBussiness) {
      val invitation: Invitation = await(createInvitation(arn, client))
      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = controller.rejectInvitation(client.urlIdentifier, client.wrongIdentifier.value, invitationId)(request)

      status(result) shouldBe 403

      val invitationResult: Result = await(controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Pending))(getResult))
      val testInvitationOpt: Option[TestHalResponseInvitation] = (jsonBodyOf(invitationResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Pending.toString)
    }
  }

  "GET /clients/:clientIdType/:clientId/invitations/received/:invitationId" should {
    val request = FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/received/:invitationId")

    "return invitation as expected" in new LoggedInUser(false) {
      uiClients.foreach { client =>
        val invitation: Invitation = await(createInvitation(arn, client))
        val result = await(controller.getInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
        status(result) shouldBe 200
      }
    }

    "return bad_request for invalid clientType and clientId combination" in new LoggedInUser(false) {
      uiClients.foreach { client =>
        val invalidClient = client.copy(urlIdentifier = client.urlIdentifier.toLowerCase)
        val result = await(controller.getInvitation(invalidClient.urlIdentifier, client.clientId.value, InvitationId("D123456789"))(request))
        status(result) shouldBe 400
      }
    }
  }

  "GET /clients/:service/:taxIdentifier/invitations/received" should {
    uiClients.foreach { client =>
      runGetAllInvitationsScenario(client, true)
      runGetAllInvitationsScenario(client, false)
    }

    runGetAllInvitationsScenario(cgtClientBus,  false, true)
    runGetAllInvitationsScenario(cgtClientBus, true, true)
  }

  private def runGetAllInvitationsScenario[T<:TaxIdentifier](testClient: TestClient[T], forStride: Boolean, forBusiness: Boolean = false) = {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")
    s"return 200 for get all ${testClient.service.id} invitations for: ${testClient.clientId.value} logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      await(createInvitation(arn, testClient))
      await(createInvitation(arn2, testClient))

      val result: Result = await(controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request))

      status(result) shouldBe 200

      val json = (jsonBodyOf(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    s"return 200 for getting no ${testClient.service.id} invitations for ${testClient.clientId.value} logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      val result = await(controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request))

      status(result) shouldBe 200

      val json = (jsonBodyOf(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 0
    }
  }
}
