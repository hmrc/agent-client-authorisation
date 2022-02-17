package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentclientauthorisation.util.DateUtils
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ClientInvitationsControllerISpec extends BaseISpec with RelationshipStubs with EmailStub with PlatformAnalyticsStubs {

  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]
  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

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
      LocalDate.now().plusDays(21),
      None)
  }

  def createEmailInfo(dfe: DetailsForEmail,
                      expiryDate: String,
                      templateId: String,
                      service: Service): EmailInformation = {

    val serviceText = service match {
      case MtdIt => "manage their Making Tax Digital for Income Tax."
      case PersonalIncomeRecord => "view their PAYE income record."
      case Vat => "manage their VAT."
      case Trust => "maintain a trust or an estate."
      case TrustNT => "maintain a trust or an estate."
      case CapitalGains => "manage their Capital Gains Tax on UK property account."
      case Ppt => "manage their Plastic Packaging Tax."
    }

    EmailInformation(Seq(dfe.agencyEmail),
      templateId,
      Map("agencyName"   -> s"${dfe.agencyName}",
        "clientName"   -> s"${dfe.clientName}",
        "expiryDate" -> expiryDate,
        "service"      -> s"$serviceText"))
  }

  class LoggedInUser(forStride: Boolean, forBusiness: Boolean = false, altStride: Boolean = false) {
    if(forStride) {
      if (altStride) {
        givenUserIsAuthenticatedWithStride(ALT_STRIDE_ROLE, "strideId-1234456")
      } else {
        givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
      }
    } else if(forBusiness)
      givenClientAllBusCgt(cgtRefBus)
    else
      givenClientAll(mtdItId, vrn, nino, utr, urn, cgtRef, pptRef)
  }

  trait AddEmailSupportStub {
    givenGetAgencyDetailsStub(arn)
    givenNinoForMtdItId(mtdItId, nino)
    hasABusinessPartnerRecord(nino)
    givenCitizenDetailsAreKnownFor(nino.value, "19122019")
    givenClientDetailsForVat(vrn)
    val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""
    getTrustName(utr.value, response = trustNameJson)
    getTrustName(urn.value, response = trustNameJson)
    getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())
    getCgtSubscription(cgtRefBus, 200, Json.toJson(cgtSubscriptionBus).toString())
  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/accept" should {
    (altItsaClient::uiClients).foreach { client =>
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

    //runAcceptInvitationsScenarioForAlternativeItsa(altItsaClient, "UI", false)
  }

  def runAcceptInvitationsScenario[T<: TaxIdentifier](client: TestClient[T], journey: String, forStride: Boolean, forBusiness: Boolean = false): Unit = {

  val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/accept")
    val getResult = FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/:invitationId")

    s"accept via $journey ${client.urlIdentifier} ${client.service.id} ${if(client.isAltItsaClient)"(ALT-ITSA) " else "" }invitation as expected for ${client.clientId.value} ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) with AddEmailSupportStub {
      val invitation: Invitation = await(createInvitation(arn, client))
      if(!client.isAltItsaClient) givenCreateRelationship(arn, client.service.id, if(client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
      givenEmailSent(createEmailInfo(dfe(client.clientName),DateUtils.displayDate(invitation.expiryDate), "client_accepted_authorisation_request", client.service))
      anAfiRelationshipIsCreatedWith(arn, client.clientId)
      givenPlatformAnalyticsRequestSent(true)

      val result: Result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204

      if(client.isAltItsaClient) givenMtdItIdIsUnknownFor(nino)

      val updatedInvitation: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, if(client.isAltItsaClient) Some(PartialAuth) else Some(Accepted))(getResult)
      val testInvitationOpt: Option[TestHalResponseInvitation] = (contentAsJson(updatedInvitation) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      val testHasStatusOf: InvitationStatus = if(client.isAltItsaClient) PartialAuth else Accepted
      testInvitationOpt.map(_.status) shouldBe Some(testHasStatusOf.toString)
      verifyAnalyticsRequestSent(1)
    }

    s"return via $journey bad_request for invalid clientType and clientId: ${client.clientId.value} ${client.urlIdentifier} ${if(client.isAltItsaClient)"(ALT-ITSA)" else "" } combination ${if(forStride) "stride" else "client"}" in new LoggedInUser(false, forBusiness) {
      val invalidClient: TestClient[T] = client.copy[T](urlIdentifier = client.urlIdentifier.toLowerCase)
      val result: Result = await(controller.acceptInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, InvitationId("D123456789"))(request))
      status(result) shouldBe 400
    }

    s"attempting via $journey to accept an ${client.service.id} ${if(client.isAltItsaClient)"(ALT-ITSA)" else "" } invitation that does not exist for ${client.clientId.value} logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      val result: Future[Result] = controller.acceptInvitation(client.urlIdentifier, client.clientId.value, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting via $journey to accept an ${client.service.id} ${if(client.isAltItsaClient)"(ALT-ITSA)" else "" } invitation that is not for the client: ${client.clientId.value} if logged in as ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      val invitation: Invitation = await(createInvitation(arn, client))
      val invitationId: InvitationId = invitation.invitationId

      val result: Future[Result] = controller.acceptInvitation(client.urlIdentifier, client.wrongIdentifier.value, invitationId)(request)

      status(result) shouldBe 403

      val invitationResult: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Pending))(getResult)
      val testInvitationOpt: Option[TestHalResponseInvitation] = (contentAsJson(invitationResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Pending.toString)
    }

    s"accepting via $journey to accept an ${client.service.id} ${if(client.isAltItsaClient)"(ALT-ITSA)" else "" } invitation should mark existing invitations as de-authed for the client: ${client.clientId.value} if logged in as ${if(forStride) "stride" else "client"}" in new LoggedInUser(false, forBusiness) {
      val oldInvitation: Invitation = await(createInvitation(arn, client))
      val acceptedStatus: InvitationStatus = if(client.isAltItsaClient) PartialAuth else Accepted
      await(repository.update(oldInvitation, acceptedStatus, DateTime.now()))

      val clientOnDifferentService: TestClient[_ >: MtdItId with Vrn <: TaxIdentifier] = if(client.service == Service.Vat) itsaClient else vatClient

      val oldInvitationFromDifferentService: Invitation = await(createInvitation(arn, clientOnDifferentService))
      await(repository.update(oldInvitationFromDifferentService, Accepted, DateTime.now()))

      val invitation: Invitation = await(createInvitation(arn, client))
      if(!client.isAltItsaClient) givenCreateRelationship(arn, client.service.id, if(client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
      givenEmailSent(createEmailInfo(dfe(client.clientName),DateUtils.displayDate(invitation.expiryDate), "client_accepted_authorisation_request", client.service))
      if(!client.isAltItsaClient) givenClientRelationships(arn, client.service.id)

      anAfiRelationshipIsCreatedWith(arn, client.clientId)
      givenPlatformAnalyticsRequestSent(true)

      // New invitation should be "accepted"
      val result: Result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204
      val updatedInvitation: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(acceptedStatus))(getResult)
      val testInvitationOpt: Option[TestHalResponseInvitation] = (contentAsJson(updatedInvitation) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(acceptedStatus.toString)
      verifyAnalyticsRequestSent(1)

      // Old invitation should be "deauthorised"
      val oldInvitationResult: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(DeAuthorised))(getResult)
      val oldInvitationOpt: Option[TestHalResponseInvitation] = (contentAsJson(oldInvitationResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      oldInvitationOpt.map(_.status) shouldBe Some(DeAuthorised.toString)

      // Invitation on different service should stay "accepted", can't run this for capital gains, as other services do not have the same permissions
      if (client.service != Service.CapitalGains) {
        val oldInvitationOnDifferentServiceResult: Future[Result] = controller.getInvitations(clientOnDifferentService.urlIdentifier, clientOnDifferentService.clientId.value, Some(Accepted))(getResult)
        val oldInvitationOnDifferentServiceOpt: Option[TestHalResponseInvitation] = (contentAsJson(oldInvitationOnDifferentServiceResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
        oldInvitationOnDifferentServiceOpt.map(_.status) shouldBe Some(Accepted.toString)
      }
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

  def runRejectInvitationsScenario[T<:TaxIdentifier](client: TestClient[T], journey: String, forStride: Boolean, forBussiness: Boolean = false): Unit = {
    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/reject")
    val getResult = FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/:invitationId")

    s"reject via $journey ${client.urlIdentifier} ${client.service.id} invitation for ${client.clientId.value} as expected with ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBussiness) with AddEmailSupportStub {
      val invitation: Invitation = await(createInvitation(arn, client))
      givenEmailSent(createEmailInfo(dfe(client.clientName), DateUtils.displayDate(invitation.expiryDate), "client_rejected_authorisation_request", client.service))
        givenPlatformAnalyticsRequestSent(true)

        val result: Result = await(controller.rejectInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
        status(result) shouldBe 204
      val updatedInvitation: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Rejected))(getResult)
      val testInvitationOpt: Option[TestHalResponseInvitation] = (contentAsJson(updatedInvitation) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Rejected.toString)
      verifyAnalyticsRequestSent(1)
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

      val invitationResult: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Pending))(getResult)
      val testInvitationOpt: Option[TestHalResponseInvitation] = (contentAsJson(invitationResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
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
      runGetAllInvitationsScenario(client, forStride = true)
      runGetAllInvitationsScenario(client, forStride = false)
    }

    runGetAllInvitationsScenario(cgtClientBus,  forStride = false, forBusiness = true)
    runGetAllInvitationsScenario(cgtClientBus, forStride = true, forBusiness = true)
    runGetAllInvitationsScenario(altItsaClient, forStride = false)
    runGetAllInvitationsAltItsaScenario(altItsaClient, forStride = true)
    runGetAllInvitationsAltItsaScenario(altItsaClient, forStride = true, altStride = true)
    runGetAllInvitationsAltItsaScenario(altItsaClient, forStride = false)
  }

  private def runGetAllInvitationsScenario[T<:TaxIdentifier](testClient: TestClient[T], forStride: Boolean, forBusiness: Boolean = false): Unit = {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")
    s"return 200 for get all ${testClient.service.id} invitations for: ${testClient.clientId.value} logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      await(createInvitation(arn, testClient))
      await(createInvitation(arn2, testClient))

      val result: Future[Result] = controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request)

      status(result) shouldBe 200

      val json: TestHalResponseInvitations = (contentAsJson(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
    }

    s"return 200 for getting no ${testClient.service.id} invitations for ${testClient.clientId.value} logged in ${if(forStride) "stride" else "client"}" in new LoggedInUser(forStride, forBusiness) {
      val result: Future[Result] = controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request)

      status(result) shouldBe 200

      val json: TestHalResponseInvitations = (contentAsJson(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 0
    }
  }

  private def runGetAllInvitationsAltItsaScenario[T<:TaxIdentifier](testClient: TestClient[T], forStride: Boolean, altStride: Boolean = false): Unit = {
    val request = FakeRequest("GET", "/clients/:service/:identifier/invitations/received")
    s"return 200 for get all ${testClient.service.id} (ALT-ITSA) invitations for: ${testClient.clientId.value} logged in ${if(forStride) if(altStride) "alt-stride" else "stride" else "client"}" in new LoggedInUser(forStride, forBusiness = false, altStride = altStride) {
      await(createInvitation(arn, testClient))
      await(createInvitation(arn2, testClient))
      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result: Future[Result] = controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request)

      status(result) shouldBe 200

      val json: TestHalResponseInvitations = (contentAsJson(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 2
      json.invitations.head.clientId shouldBe mtdItId.value
      json.invitations.last.clientId shouldBe mtdItId.value
    }

    s"return 200 for get all ${testClient.service.id} (ALT-ITSA) invitations for: ${testClient.clientId.value} logged in ${if(forStride) if(altStride) "alt-stride" else "stride" else "client"} and " +
      s"update status to Accepted when PartialAuth exists" in new LoggedInUser(forStride, false, altStride = altStride) {
      val pendingInvitation: Invitation = await(createInvitation(arn, testClient))
      await(repository.update(pendingInvitation, PartialAuth, DateTime.now()))
      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result: Future[Result] = controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request)
      status(result) shouldBe 200

      val json: TestHalResponseInvitations = (contentAsJson(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 1
      json.invitations.head.clientId shouldBe mtdItId.value
      json.invitations.head.status shouldBe "Accepted"
    }

    s"return 200 for getting no ${testClient.service.id} (ALT-ITSA) invitations for ${testClient.clientId.value} logged in ${if(forStride) if(altStride) "alt-stride" else "stride" else "client"}" in new LoggedInUser(forStride, false, altStride = altStride) {
      val result: Future[Result] = controller.getInvitations(testClient.urlIdentifier, testClient.clientId.value, None)(request)

      status(result) shouldBe 200

      val json: TestHalResponseInvitations = (contentAsJson(result) \ "_embedded").as[TestHalResponseInvitations]
      json.invitations.length shouldBe 0
    }
  }
}
