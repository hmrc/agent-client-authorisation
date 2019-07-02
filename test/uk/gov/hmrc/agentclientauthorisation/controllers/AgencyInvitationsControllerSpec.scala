/*
 * Copyright 2019 HM Revenue & Customs
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

import com.kenshoo.play.metrics.Metrics
import javax.inject.Provider
import org.joda.time.format.DateTimeFormat
import org.joda.time.{DateTime, LocalDate}
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json._
import play.api.mvc.Results.{Accepted => AcceptedResponse, _}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegments
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, AuthActions, MicroserviceAuthConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, Vrn}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.Upstream4xxResponse

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AgencyInvitationsControllerSpec
    extends AkkaMaterializerSpec with ResettingMockitoSugar with BeforeAndAfterEach with TransitionInvitation
    with TestData {

  val postcodeService: PostcodeService = resettingMock[PostcodeService]
  val invitationsService: InvitationsService = resettingMock[InvitationsService]
  val multiInvitationsService: AgentLinkService = resettingMock[AgentLinkService]
  val agentServicesaccountConnector: AgentServicesAccountConnector = resettingMock[AgentServicesAccountConnector]
  val kfcService: KnownFactsCheckService = resettingMock[KnownFactsCheckService]
  val generator = new Generator()
  val authConnector: AuthActions = resettingMock[AuthActions]
  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]
  val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }

  val jsonBody = Json.parse(
    s"""{"service": "HMRC-MTD-IT", "clientIdType": "ni", "clientId": "$nino1", "clientPostcode": "BN124PJ"}""")

  val controller =
    new AgencyInvitationsController(
      "http://localhost:9448",
      postcodeService,
      invitationsService,
      kfcService,
      multiInvitationsService,
      agentServicesaccountConnector)(metrics, microserviceAuthConnector, ecp) {
      override val authConnector: PlayAuthConnector = mockPlayAuthConnector
    }

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(
      mockPlayAuthConnector
        .authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any(), any[ExecutionContext]))
      .thenReturn(returnValue)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(invitationsService.findInvitationsBy(eqs(Some(arn)), eqs(None), eqs(None), eqs(None), eqs(None))(any(), any()))
      .thenReturn(Future successful allInvitations)

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Some(Service.MtdIt)), eqs(None), eqs(None), eqs(None))(any(), any()))
      .thenReturn(Future successful allInvitations.filter(_.service.id == "HMRC-MTD-IT"))

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(None), eqs(None), eqs(Some(Accepted)), eqs(None))(any(), any()))
      .thenReturn(Future successful allInvitations.filter(_.status == Accepted))

    when(
      invitationsService.findInvitationsBy(
        eqs(Some(arn)),
        eqs(Some(Service.MtdIt)),
        any[Option[String]],
        eqs(Some(Accepted)),
        eqs(None))(any(), any())).thenReturn(Future successful allInvitations.filter(_.status == Accepted))
  }

  "createInvitation" should {
    "create an invitation when given correct Arn" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      when(postcodeService.postCodeMatches(any[String](), any[String]())(any(), any()))
        .thenReturn(Future successful None)
      when(invitationsService.translateToMtdItId(any[String](), any[String]())(any(), any()))
        .thenReturn(Future successful Some(ClientIdentifier(mtdItId1)))
      when(invitationsService.create(any[Arn](), any(), any[Service](), any(), any())(any(), any()))
        .thenReturn(Future successful inviteCreated)

      val response = await(controller.createInvitation(arn)(FakeRequest().withJsonBody(jsonBody)))

      status(response) shouldBe 201
      response.header.headers.get("Location") shouldBe Some(
        s"/agencies/arn1/invitations/sent/${mtdSaPendingInvitationId.value}")
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

      when(multiInvitationsService.getAgentLink(any[Arn], any())(any(), any())).thenReturn(Future successful "/foo")
      val response = await(controller.getAgentLink(arn, "personal")(FakeRequest()))

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
        inviteCreated.copy(
          events = List(StatusChangeEvent(DateTime.now(), Pending), StatusChangeEvent(DateTime.now(), Cancelled)))

      when(invitationsService.findInvitation(any())(any(), any(), any()))
        .thenReturn(Future successful Some(inviteCreated))
      when(invitationsService.cancelInvitation(eqs(inviteCreated))(any(), any()))
        .thenReturn(Future successful Right(cancelledInvite))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      status(response) shouldBe 204
    }

    "return 403 when trying to cancel an already accepted invitation" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      when(invitationsService.findInvitation(any())(any(), any(), any()))
        .thenReturn(Future successful Some(inviteCreated))
      when(invitationsService.cancelInvitation(eqs(inviteCreated))(any(), any()))
        .thenReturn(Future successful Left(StatusUpdateFailure(Accepted, "already accepted")))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      response shouldBe invalidInvitationStatus("already accepted")
    }

    "return 404 when trying to cancel a not found invitation" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(invitationsService.findInvitation(any())(any(), any(), any()))
        .thenReturn(Future successful None)

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      response shouldBe InvitationNotFound
    }
  }

  "getSentInvitations" should {

    "for a matching agency should return the invitations" in {

      agentAuthStub(agentAffinityAndEnrolments)

      when(multiInvitationsService.getAgentLink(any[Arn], any())(any(), any())).thenReturn(Future successful "/foo")

      val response = await(controller.getSentInvitations(arn, None, None, None, None, None, None)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 3

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaPendingInvitationId)
      invitationLink(jsonBody, 1) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
      invitationLink(jsonBody, 2) shouldBe expectedAgencySentInvitationLink(arn, otherRegimePendingInvitationId)
    }

    "include the invitation ID in invitations" in {

      agentAuthStub(agentAffinityAndEnrolments)

      when(multiInvitationsService.getAgentLink(any[Arn], any())(any(), any())).thenReturn(Future successful "/foo")

      val response = await(controller.getSentInvitations(arn, None, None, None, None, None, None)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 3

      (embeddedInvitations(jsonBody)(0) \ "status").asOpt[String] should not be None
      (embeddedInvitations(jsonBody)(0) \ "id").asOpt[String] shouldBe None
      (embeddedInvitations(jsonBody)(0) \ "invitationId").asOpt[String] shouldBe Some("ASZPJLZMLKRBO")
    }

    "include all query parameters in the self link" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val clientType = Some("personal")
      val service = Some("HMRC-MTD-IT")
      val clientIdType = Some("ni")
      val clientId = Some("AA123456A")
      val invitationStatus = Some(Accepted)
      val response = await(
        controller.getSentInvitations(arn, clientType, service, clientIdType, clientId, invitationStatus, None)(
          FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      (jsonBody \ "_links" \ "self" \ "href").as[String] shouldBe
        routes.AgencyInvitationsController
          .getSentInvitations(arn, clientType, service, clientIdType, clientId, invitationStatus, None)
          .url
    }

  }

  "checkKnownFactItsa" should {
    val nino = Nino("AB123456A")
    val postcode = "AA11AA"

    "return No Content if Nino is known in ETMP and the postcode matched" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any(), any()))
        .thenReturn(Future successful None)

      await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest())) shouldBe NoContent
    }

    "return 400 if given invalid postcode" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any(), any()))
        .thenReturn(Future successful Some(BadRequest))

      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 400
    }

    "return 403 if Nino is known in ETMP but the postcode did not match or not found" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any(), any()))
        .thenReturn(Future successful Some(Forbidden))

      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 403
    }

    "return 501 if Nino is known in ETMP but the postcode is non-UK" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any(), any()))
        .thenReturn(Future successful Some(NotImplemented))

      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 501
    }
  }

  "checkKnownFactVat" should {
    val vrn = Vrn("101747641")
    val suppliedDate = LocalDate.parse("2001-02-03")

    "return No Content if Vrn is known in ETMP and the effectiveRegistrationDate matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any(), any()))
        .thenReturn(Future successful Some(true))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe NoContent
    }

    "return Vat Registration Date Does Not Match if Vrn is known in ETMP and the effectiveRegistrationDate did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any(), any()))
        .thenReturn(Future successful Some(false))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe VatRegistrationDateDoesNotMatch
    }

    "return Not Found if Vrn is unknown in ETMP" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any(), any()))
        .thenReturn(Future successful None)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe NotFound
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      agentAuthStub(agentNoEnrolments)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe AgentNotSubscribed
    }

    "return Locked if downstream service is in the middle of migration" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientVatRegistrationDateMatches(eqs(vrn), eqs(suppliedDate))(any(), any()))
        .thenReturn(Future failed Upstream4xxResponse("MIGRATION", 403, 423))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe Locked

    }
  }

  "checkKnownFactIrv" should {
    val format = DateTimeFormat.forPattern("ddMMyyyy")
    val nino = Nino("AB123456A")
    val suppliedDateOfBirth = LocalDate.parse("03022001", format)

    "return No Content if Nino is known in citizen details and the dateOfBirth matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any(), any()))
        .thenReturn(Future successful Some(true))

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe NoContent
    }

    "return Date Of Birth Does Not Match if Nino is known in citizen details and the dateOfBirth did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any(), any()))
        .thenReturn(Future successful Some(false))

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe DateOfBirthDoesNotMatch
    }

    "return Not Found if Nino is unknown in citizen details" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(kfcService.clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any(), any()))
        .thenReturn(Future successful None)

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe NotFound
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      agentAuthStub(agentNoEnrolments)

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe AgentNotSubscribed
    }
  }

  "normalise agencyName" in {
    controller.normaliseAgentName("My age%*&nt name") shouldBe "my-agent-name"
    controller.normaliseAgentName("Paul  Tan 8 ()^£$???") shouldBe "paul-tan-8-"
  }

  private def invitationLink(agencyInvitationsSent: JsValue, idx: Int): String =
    (embeddedInvitations(agencyInvitationsSent)(idx) \ "_links" \ "self" \ "href").as[String]

  private def invitationsSize(agencyInvitationsSent: JsValue): Int =
    embeddedInvitations(agencyInvitationsSent).value.size

  private def embeddedInvitations(agencyInvitationsSent: JsValue): JsArray =
    (agencyInvitationsSent \ "_embedded" \ "invitations").as[JsArray]

  private def expectedAgencySentInvitationLink(arn: Arn, invitationId: InvitationId) =
    encodePathSegments(
      // TODO I would expect the links to start with "/agent-client-authorisation", however it appears they don't and that is not the focus of what I'm testing at the moment
      // "agent-client-authorisation",
      "agencies",
      arn.value,
      "invitations",
      "sent",
      invitationId.value
    )

  private def anInvitation(arn: Arn = arn) =
    TestConstants.defaultInvitation
      .copy(id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn)

  private def aFutureOptionInvitation(arn: Arn = arn) =
    Future successful Some(anInvitation(arn))

  private def whenFindingAnInvitation() =
    when(invitationsService.findInvitation(any[InvitationId])(any(), any(), any()))

  private def whenAnInvitationIsCancelled(implicit ec: ExecutionContext) =
    when(invitationsService.cancelInvitation(any[Invitation])(any(), any()))
}
