/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.concurrent.{DefaultFutures, Futures}
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers.stubControllerComponents
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, Citizen, CitizenDetailsConnector, DesConnector, DesignatoryDetails}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.Service.Trust
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, _}
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{nino, _}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentclientauthorisation.util.{failure, valueOps}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Vrn}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AgencyInvitationsControllerSpec
    extends MocksWithCache with AkkaMaterializerSpec with ResettingMockitoSugar with BeforeAndAfterEach with TransitionInvitation with TestData {

  val postcodeService: PostcodeService = resettingMock[PostcodeService]
  val invitationsService: InvitationsService = resettingMock[InvitationsService]
  val multiInvitationsService: AgentLinkService = resettingMock[AgentLinkService]
  val kfcService: KnownFactsCheckService = resettingMock[KnownFactsCheckService]
  val generator = new Generator()
  val authActions: AuthActions = resettingMock[AuthActions]
  val cc: ControllerComponents = stubControllerComponents()
  val metrics: Metrics = resettingMock[Metrics]
  val appConfig: AppConfig = resettingMock[AppConfig]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  override val mockDesConnector = resettingMock[DesConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)
  override val agentCacheProvider = resettingMock[AgentCacheProvider]
  override val mockCitizenDetailsConnector = resettingMock[CitizenDetailsConnector]
  val futures: Futures = new DefaultFutures(ActorSystem("TestSystem"))

  val jsonBody = Json.parse(s"""{"service": "HMRC-MTD-IT", "clientIdType": "ni", "clientId": "$nino1", "clientPostcode": "BN124PJ"}""")

  val controller =
    new AgencyInvitationsController(
      appConfig,
      postcodeService,
      invitationsService,
      kfcService,
      multiInvitationsService,
      mockDesConnector,
      mockPlayAuthConnector,
      mockCitizenDetailsConnector,
      agentCacheProvider
    )(metrics, cc, futures, global) {}

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[Predicate], any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any[HeaderCarrier], any[ExecutionContext]))
      .thenReturn(returnValue)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Seq.empty[Service]), eqs(None), eqs(None), eqs(None))(any[ExecutionContext]))
      .thenReturn(Future successful allInvitations)

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Seq(Service.MtdIt)), eqs(None), eqs(None), eqs(None))(any[ExecutionContext]))
      .thenReturn(Future successful allInvitations.filter(_.service.id == "HMRC-MTD-IT"))

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Seq.empty[Service]), eqs(None), eqs(Some(Accepted)), eqs(None))(any[ExecutionContext]))
      .thenReturn(Future successful allInvitations.filter(_.status == Accepted))

    when(
      invitationsService.findInvitationsBy(eqs(Some(arn)), eqs(Seq(Service.MtdIt)), any[Option[String]], eqs(Some(Accepted)), eqs(None))(
        any[ExecutionContext]))
      .thenReturn(Future successful allInvitations.filter(_.status == Accepted))
    ()
  }

  "replace URN invitation with UTR" should {
    "return 404 when no invitation found" in {

      when(invitationsService.findLatestInvitationByClientId(any[String])(any[ExecutionContext]))
        .thenReturn(None)

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

      status(response) shouldBe 404
    }

    "return 204 when there is an invitation which isn't pending or existing" in {

      when(invitationsService.findLatestInvitationByClientId(any[String])(any[ExecutionContext]))
        .thenReturn(Some(invitationExpired))

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

      status(response) shouldBe 204
    }

    "return 201 and end existing relationship when an active invitation is found" in {

      when(invitationsService.findLatestInvitationByClientId(any[String])(any[ExecutionContext]))
        .thenReturn(Some(invitationActive))
      when(invitationsService.setRelationshipEnded(any[Invitation], any[String])(any[ExecutionContext]))
        .thenReturn(Future.successful(invitationActive))
      when(
        invitationsService
          .create(any[Arn](), any[Option[String]], eqs(Trust), any[ClientIdentifier.ClientId], any[ClientIdentifier.ClientId], any[Option[String]])(
            any[HeaderCarrier],
            any[ExecutionContext]))
        .thenReturn(Future successful invitationActive)
      when(invitationsService.acceptInvitationStatus(any[Invitation])(any[ExecutionContext]))
        .thenReturn(Future successful invitationActive)

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

      status(response) shouldBe 201
    }

    "return 201 when a pending invitation is found" in {
      when(invitationsService.findLatestInvitationByClientId(any[String])(any[ExecutionContext]))
        .thenReturn(Some(invitationPending))
      when(
        invitationsService
          .create(any[Arn](), any[Option[String]], eqs(Trust), any[ClientIdentifier.ClientId], any[ClientIdentifier.ClientId], any[Option[String]])(
            any[HeaderCarrier],
            any[ExecutionContext]))
        .thenReturn(Future successful invitationActive)
      when(invitationsService.cancelInvitation(any[Invitation])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(invitationActive)

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

      status(response) shouldBe 201
    }
  }

  "createInvitation" should {
    "create an invitation when given correct Arn" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      when(postcodeService.postCodeMatches(any[String](), any[String]())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(().toFuture)
      when(invitationsService.getClientIdForItsa(any[String](), any[String]())(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful Some(ClientIdentifier(mtdItId1)))
      when(
        invitationsService.create(
          any[Arn](),
          any[Option[String]],
          any[Service](),
          any[ClientIdentifier.ClientId],
          any[ClientIdentifier.ClientId],
          any[Option[String]])(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful inviteCreated)

      val response = await(controller.createInvitation(arn)(FakeRequest().withJsonBody(jsonBody)))

      status(response) shouldBe 201
      response.header.headers.get("Location") shouldBe Some(s"/agencies/arn1/invitations/sent/${mtdSaPendingInvitationId.value}")
      response.header.headers.get("InvitationId") shouldBe Some(
        mtdSaPendingInvitationId.value
      )
    }

    "not create an invitation when given arn is incorrect" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val response = await(controller.createInvitation(new Arn("1234"))(FakeRequest().withJsonBody(jsonBody)))

      status(response) shouldBe 403
    }
  }

  "getAgentLink" should {
    "create a agent link and store it in the headers" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(multiInvitationsService.getInvitationUrl(any[Arn], any[String])(any[ExecutionContext], any[HeaderCarrier]))
        .thenReturn(Future successful "/foo")
      val response = await(controller.getInvitationUrl(arn, "personal")(FakeRequest()))

      status(response) shouldBe 201
      response.header.headers.get("Location") shouldBe Some("/foo")
    }
  }

  "cancelInvitation" should {
    "return 204 when successfully changing the status of an invitation to cancelled" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      val cancelledInvite =
        inviteCreated.copy(events = List(StatusChangeEvent(DateTime.now(), Pending), StatusChangeEvent(DateTime.now(), Cancelled)))

      when(
        invitationsService
          .findInvitation(any[InvitationId])(any[ExecutionContext]))
        .thenReturn(Future successful Some(inviteCreated))
      when(invitationsService.cancelInvitation(eqs(inviteCreated))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful cancelledInvite)

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      status(response) shouldBe 204
    }

    "return 403 when trying to cancel an already accepted invitation" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      when(
        invitationsService
          .findInvitation(any[InvitationId])(any[ExecutionContext]))
        .thenReturn(Future successful Some(inviteCreated))
      when(invitationsService.cancelInvitation(eqs(inviteCreated))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future failed StatusUpdateFailure(Accepted, "already accepted"))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      response shouldBe invalidInvitationStatus("already accepted")
    }

    "return 404 when trying to cancel a not found invitation" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        invitationsService
          .findInvitation(any[InvitationId])(any[ExecutionContext]))
        .thenReturn(Future successful None)

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      response shouldBe InvitationNotFound
    }
  }

  "checkKnownFactItsa" should {
    val nino = Nino("AB123456A")
    val postcode = "AA11AA"

    "return No Content if Nino is known in ETMP and the postcode matched" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(().toFuture)

      await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest())) shouldBe NoContent
    }

    "return 400 if given invalid postcode" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(failure(BadRequest))

      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 400
    }

    "return 403 if Nino is known in ETMP but the postcode did not match or not found and NOT in Citizen Details" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(failure(Forbidden))
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(None)
      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 403
    }

    "return No Content if Nino is known in ETMP but the postcode did not match or not found and IS in Citizen Details without an SAUTR" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(().toFuture)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None)))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(DesignatoryDetails(Some(nino.nino), Some(postcode))))
      await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest())) shouldBe NoContent
    }

    "return No Content if Nino is known in ETMP but the postcode did not match or not found and IS in Citizen Details with an SAUTR" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(().toFuture)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), Some(utr.value))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(DesignatoryDetails(Some(nino.nino), Some(postcode))))
      await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest())) shouldBe NoContent
    }

    "return 501 if Nino is known in ETMP but the postcode is non-UK" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(failure(NotImplemented))

      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 501
    }
  }

  "checkPostcodeAgainstCitizenDetails" should {
    val postcode = "AA1 1AA"

    "return 204 if postcode matches in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), Some(utr.value))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(DesignatoryDetails(Some(nino.nino), Some(postcode))))
      await(controller.checkPostcodeAgainstCitizenDetails(nino, postcode)(HeaderCarrier())).header.status shouldBe 204
    }

    "return 403 if postcode doesn't match in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None)))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(DesignatoryDetails(Some(nino.nino), Some("BB2 2BB"))))
      await(controller.checkPostcodeAgainstCitizenDetails(nino, postcode)(HeaderCarrier())).header.status shouldBe 403
    }
    "return 403 if postcode not found in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None)))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(DesignatoryDetails(Some(nino.nino), None)))
      controller.checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(HeaderCarrier()).header.status shouldBe 403
    }
    "return 403 if nino not found in DesignatoryDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None)))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(None)
      controller.checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(HeaderCarrier()).header.status shouldBe 403
    }
    "return 403 if nino not found in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(None)
      controller.checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(HeaderCarrier()).header.status shouldBe 403
    }
  }

  "checkKnownFactVat" should {
    val vrn = Vrn("101747641")
    val suppliedDate = LocalDate.parse("2001-02-03")

    "return No Content if Vrn is known in ETMP and the effectiveRegistrationDate matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful Some(true))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe NoContent
    }

    "return Vat Registration Date Does Not Match if Vrn is known in ETMP and the effectiveRegistrationDate did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful Some(false))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe VatRegistrationDateDoesNotMatch
    }

    "return Not Found if Vrn is unknown in ETMP" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful None)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe NotFound
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      agentAuthStub(agentNoEnrolments)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe AgentNotSubscribed
    }

    "return Locked if downstream service is in the middle of migration" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future failed UpstreamErrorResponse("MIGRATION", 403, 423))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe Locked

    }
  }

  "checkKnownFactIrv" should {
    val format = DateTimeFormat.forPattern("ddMMyyyy")
    val nino = Nino("AB123456A")
    val suppliedDateOfBirth = LocalDate.parse("03022001", format)

    "return No Content if Nino is known in citizen details and the dateOfBirth matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful Some(true))

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe NoContent
    }

    "return Date Of Birth Does Not Match if Nino is known in citizen details and the dateOfBirth did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful Some(false))

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe DateOfBirthDoesNotMatch
    }

    "return Not Found if Nino is unknown in citizen details" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful None)

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe NotFound
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      agentAuthStub(agentNoEnrolments)

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe AgentNotSubscribed
    }
  }
}
