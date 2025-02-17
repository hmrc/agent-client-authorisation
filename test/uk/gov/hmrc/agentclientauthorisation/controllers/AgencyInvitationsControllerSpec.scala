/*
 * Copyright 2023 HM Revenue & Customs
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

import org.apache.pekko.actor.ActorSystem
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.concurrent.{DefaultFutures, Futures}
import play.api.libs.json._
import play.api.mvc.ControllerComponents
import play.api.mvc.Results._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.Pillar2KnownFactCheckResult._
import uk.gov.hmrc.agentclientauthorisation.model.VatKnownFactCheckResult._
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, _}
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentclientauthorisation.util.{failure, valueOps}
import uk.gov.hmrc.agentmtdidentifiers.model.Service.Trust
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.http.{Authorization, HeaderCarrier, UpstreamErrorResponse}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class AgencyInvitationsControllerSpec
    extends MocksWithCache with AkkaMaterializerSpec with ResettingMockitoSugar with BeforeAndAfterEach with TransitionInvitation with TestData {

  implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("Bearer testtoken")))
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
  override val mockIfConnector = resettingMock[IfConnector]
  override val mockEisConnector = resettingMock[EisConnector]
  val auditConnector: AuditConnector = resettingMock[AuditConnector]
  val auditService: AuditService = new AuditService(auditConnector)
  override val agentCacheProvider = resettingMock[AgentCacheProvider]
  override val mockCitizenDetailsConnector = resettingMock[CitizenDetailsConnector]
  val mockRelationshipsConnector: RelationshipsConnector = resettingMock[RelationshipsConnector]
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
      mockIfConnector,
      mockEisConnector,
      mockPlayAuthConnector,
      mockCitizenDetailsConnector,
      agentCacheProvider,
      mockRelationshipsConnector
    )(metrics, cc, futures, global) {}

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(
      mockPlayAuthConnector
        .authorise(any[Predicate], any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any[HeaderCarrier], any[ExecutionContext])
    )
      .thenReturn(returnValue)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Seq.empty[Service]), eqs(None), eqs(None), eqs(None))(any[HeaderCarrier])
    )
      .thenReturn(Future successful allInvitations)

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Seq(Service.MtdIt)), eqs(None), eqs(None), eqs(None))(any[HeaderCarrier])
    )
      .thenReturn(Future successful allInvitations.filter(_.service.id == "HMRC-MTD-IT"))

    when(
      invitationsService
        .findInvitationsBy(eqs(Some(arn)), eqs(Seq.empty[Service]), eqs(None), eqs(Some(Accepted)), eqs(None))(any[HeaderCarrier])
    )
      .thenReturn(Future successful allInvitations.filter(_.status == Accepted))

    when(
      invitationsService.findInvitationsBy(eqs(Some(arn)), eqs(Seq(Service.MtdIt)), any[Option[String]], eqs(Some(Accepted)), eqs(None))(
        any[HeaderCarrier]
      )
    )
      .thenReturn(Future successful allInvitations.filter(_.status == Accepted))
    ()
  }

  "replace URN invitation with UTR" when {

    "the acr-mongo-activated feature switch is enabled" should {

      "return 204 when an invitation is found and updated in ACR" in {
        when(appConfig.acrMongoActivated).thenReturn(true)
        when(mockRelationshipsConnector.replaceUrnWithUtr(any[String], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(true))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 204
      }

      "return 204 when an invitation is not found in ACR, and ACA has an invitation that isn't Pending or Accepted" in {
        when(appConfig.acrMongoActivated).thenReturn(true)
        when(mockRelationshipsConnector.replaceUrnWithUtr(any[String], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(false))
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(Some(invitationExpired)))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 204
      }

      "return 201 when an invitation is not found in ACR, and ACA has an Accepted invitation" in {
        when(appConfig.acrMongoActivated).thenReturn(true)
        when(mockRelationshipsConnector.replaceUrnWithUtr(any[String], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(false))
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(Some(invitationActive)))
        when(invitationsService.setRelationshipEnded(any[Invitation], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(invitationActive))
        when(
          invitationsService.create(
            any[Arn](),
            any[Option[String]],
            eqs(Trust),
            any[ClientIdentifier.ClientId],
            any[ClientIdentifier.ClientId],
            any[Option[String]]
          )(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future successful invitationActive)
        when(invitationsService.acceptInvitationStatus(any[Invitation])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(Future successful invitationActive)

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 201
      }

      "return 201 when an invitation is not found in ACR, and ACA has a Pending invitation" in {
        when(appConfig.acrMongoActivated).thenReturn(true)
        when(mockRelationshipsConnector.replaceUrnWithUtr(any[String], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(false))
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(Some(invitationPending)))
        when(
          invitationsService.create(
            any[Arn](),
            any[Option[String]],
            eqs(Trust),
            any[ClientIdentifier.ClientId],
            any[ClientIdentifier.ClientId],
            any[Option[String]]
          )(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future successful invitationActive)
        when(invitationsService.cancelInvitation(any[Invitation])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(invitationActive))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 201
      }

      "return 404 when an invitation is not found in ACR or ACA" in {
        when(appConfig.acrMongoActivated).thenReturn(true)
        when(mockRelationshipsConnector.replaceUrnWithUtr(any[String], any[String])(any[HeaderCarrier]))
          .thenReturn(Future.successful(false))
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean])).thenReturn(Future.successful(None))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 404
      }
    }

    "the acr-mongo-activated feature switch is disabled" should {

      "return 404 when no invitation found" in {
        when(appConfig.acrMongoActivated).thenReturn(false)
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(None))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 404
      }

      "return 204 when there is an invitation which isn't pending or existing" in {
        when(appConfig.acrMongoActivated).thenReturn(false)
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(Some(invitationExpired)))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 204
      }

      "return 201 and end existing relationship when an active invitation is found" in {
        when(appConfig.acrMongoActivated).thenReturn(false)
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(Some(invitationActive)))
        when(invitationsService.setRelationshipEnded(any[Invitation], any[String])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(invitationActive))
        when(
          invitationsService.create(
            any[Arn](),
            any[Option[String]],
            eqs(Trust),
            any[ClientIdentifier.ClientId],
            any[ClientIdentifier.ClientId],
            any[Option[String]]
          )(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future successful invitationActive)
        when(invitationsService.acceptInvitationStatus(any[Invitation])(any[ExecutionContext], any[HeaderCarrier]))
          .thenReturn(Future successful invitationActive)

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 201
      }

      "return 201 when a pending invitation is found" in {
        when(appConfig.acrMongoActivated).thenReturn(false)
        when(invitationsService.findLatestInvitationByClientId(any[String], any[Boolean]))
          .thenReturn(Future.successful(Some(invitationPending)))
        when(
          invitationsService.create(
            any[Arn](),
            any[Option[String]],
            eqs(Trust),
            any[ClientIdentifier.ClientId],
            any[ClientIdentifier.ClientId],
            any[Option[String]]
          )(any[HeaderCarrier], any[ExecutionContext])
        ).thenReturn(Future successful invitationActive)
        when(invitationsService.cancelInvitation(any[Invitation])(any[HeaderCarrier], any[ExecutionContext]))
          .thenReturn(Future.successful(invitationActive))

        val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(FakeRequest()))

        status(response) shouldBe 201
      }
    }
  }

  "createInvitation" should {
    "create an invitation when given correct Arn" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(_id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

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
          any[Option[String]]
        )(any[HeaderCarrier], any[ExecutionContext])
      )
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
        .copy(_id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      val cancelledInvite =
        inviteCreated.copy(events = List(StatusChangeEvent(LocalDateTime.now(), Pending), StatusChangeEvent(LocalDateTime.now(), Cancelled)))

      when(
        invitationsService
          .findInvitation(any[InvitationId])(any[ExecutionContext], any[HeaderCarrier])
      )
        .thenReturn(Future successful Some(inviteCreated))

      when(invitationsService.cancelInvitation(eqs(inviteCreated))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful cancelledInvite)

      when(mockRelationshipsConnector.lookupInvitation(eqs(mtdSaPendingInvitationId.value))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future successful None)

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      status(response) shouldBe 204
    }

    "return 403 when trying to cancel an already accepted invitation" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = TestConstants.defaultInvitation
        .copy(_id = mtdSaPendingInvitationDbId, invitationId = mtdSaPendingInvitationId, arn = arn, clientId = mtdItId1)

      when(mockRelationshipsConnector.lookupInvitation(eqs(mtdSaPendingInvitationId.value))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      when(
        invitationsService
          .findInvitation(any[InvitationId])(any[ExecutionContext], any[HeaderCarrier])
      )
        .thenReturn(Future successful Some(inviteCreated))
      when(invitationsService.cancelInvitation(eqs(inviteCreated))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future failed StatusUpdateFailure(Accepted, "already accepted"))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      response shouldBe invalidInvitationStatus("already accepted")
    }

    "return 404 when trying to cancel a not found invitation" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(mockRelationshipsConnector.lookupInvitation(eqs(mtdSaPendingInvitationId.value))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))

      when(
        invitationsService
          .findInvitation(any[InvitationId])(any[ExecutionContext], any[HeaderCarrier])
      )
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
        .thenReturn(Future.successful(None))
      status(await(controller.checkKnownFactItsa(nino, postcode)(FakeRequest()))) shouldBe 403
    }

    "return No Content if Nino is known in ETMP but the postcode did not match or not found and IS in Citizen Details without an SAUTR" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(().toFuture)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(DesignatoryDetails(Some(nino.nino), Some(postcode)))))
      controller.checkKnownFactItsa(nino, postcode)(FakeRequest()).futureValue shouldBe NoContent
    }

    "return No Content if Nino is known in ETMP but the postcode did not match or not found and IS in Citizen Details with an SAUTR" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(postcodeService.postCodeMatches(eqs(nino.value), eqs(postcode))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(().toFuture)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), Some(utr.value)))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(DesignatoryDetails(Some(nino.nino), Some(postcode)))))
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
        .thenReturn(Future.successful(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), Some(utr.value)))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(DesignatoryDetails(Some(nino.nino), Some(postcode)))))
      controller.checkPostcodeAgainstCitizenDetails(nino, postcode)(HeaderCarrier()).futureValue.header.status shouldBe 204
    }

    "return 403 if postcode doesn't match in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(DesignatoryDetails(Some(nino.nino), Some("BB2 2BB")))))
      await(controller.checkPostcodeAgainstCitizenDetails(nino, postcode)(HeaderCarrier())).header.status shouldBe 403
    }
    "return 403 if postcode not found in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(DesignatoryDetails(Some(nino.nino), None))))
      controller.checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(HeaderCarrier()).futureValue.header.status shouldBe 403
    }
    "return 403 if nino not found in DesignatoryDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(Citizen(Some("Billy"), Some("Pilgrim"), Some(nino.nino), None))))
      when(mockCitizenDetailsConnector.getDesignatoryDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      controller.checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(HeaderCarrier()).futureValue.header.status shouldBe 403
    }
    "return 403 if nino not found in CitizenDetails" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockCitizenDetailsConnector.getCitizenDetails(eqs(nino))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(None))
      controller.checkPostcodeAgainstCitizenDetails(nino: Nino, postcode: String)(HeaderCarrier()).futureValue.header.status shouldBe 403
    }
  }

  "checkKnownFactVat" should {
    val vrn = Vrn("101747641")
    val suppliedDate = LocalDate.parse("2001-02-03")

    "return VatClientInsolvent if the VAT record shows the client to be insolvent" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationCheckResult(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful VatRecordClientInsolvent)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe VatClientInsolvent
    }

    "return No Content if Vrn is known in ETMP, the client is solvent and the effectiveRegistrationDate matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationCheckResult(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful VatKnownFactCheckOk)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe NoContent
    }

    "return Vat Registration Date Does Not Match if Vrn is known in ETMP, client is solvent and the effectiveRegistrationDate did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationCheckResult(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful VatKnownFactNotMatched)

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe VatRegistrationDateDoesNotMatch
    }

    "return Not Found if Vrn is unknown in ETMP" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientVatRegistrationCheckResult(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful VatDetailsNotFound)

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
          .clientVatRegistrationCheckResult(eqs(vrn), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future failed UpstreamErrorResponse("MIGRATION", 403, 423))

      await(controller.checkKnownFactVat(vrn, suppliedDate)(FakeRequest())) shouldBe Locked

    }
  }

  "checkKnownFactIrv" should {
    // val format = DateTimeFormat.forPattern("ddMMyyyy")
    val nino = Nino("AB123456A")
    val suppliedDateOfBirth = LocalDate.parse("2001-02-03")

    "return No Content if Nino is known in citizen details and the dateOfBirth matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful Some(true))

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe NoContent
    }

    "return Date Of Birth Does Not Match if Nino is known in citizen details and the dateOfBirth did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful Some(false))

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe DateOfBirthDoesNotMatch
    }

    "return Not Found if Nino is unknown in citizen details" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientDateOfBirthMatches(eqs(nino), eqs(suppliedDateOfBirth))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful None)

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe NotFound
    }

    "return Agent Not Subscribed if logged in user is not an agent with HMRC-AS-AGENT" in {
      agentAuthStub(agentNoEnrolments)

      await(controller.checkKnownFactIrv(nino, suppliedDateOfBirth)(FakeRequest())) shouldBe AgentNotSubscribed
    }
  }

  "checkKnownFactCbc" should {
    // val format = DateTimeFormat.forPattern("ddMMyyyy")
    val cbcId = CbcId("XACBC0000012345")
    val correctEmail = "client@business.org"
    val subscriptionRecord =
      SimpleCbcSubscription(tradingName = Some("My Business"), otherNames = Seq.empty, isGBUser = true, emails = Seq(correctEmail))
    def makeRequest(email: String) = FakeRequest().withJsonBody(Json.obj("email" -> email))

    "return No Content if cbcId is known and the email matched (case insensitive)" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockEisConnector.getCbcSubscription(eqs(cbcId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(subscriptionRecord)))

      await(controller.checkKnownFactCbc(cbcId)(makeRequest(correctEmail))) shouldBe NoContent
      // check case insensitivity
      await(controller.checkKnownFactCbc(cbcId)(makeRequest(correctEmail.toUpperCase))) shouldBe NoContent
    }

    "return Forbidden if cbcId is known but the email did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockEisConnector.getCbcSubscription(eqs(cbcId))(any[HeaderCarrier], any[ExecutionContext]))
        .thenReturn(Future.successful(Some(subscriptionRecord)))

      await(controller.checkKnownFactCbc(cbcId)(makeRequest("incorrect@email.com"))) shouldBe Forbidden
    }

    "return Not Found if cbcId is unknown" in {
      agentAuthStub(agentAffinityAndEnrolments)
      when(mockEisConnector.getCbcSubscription(eqs(cbcId))(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(None))

      await(controller.checkKnownFactCbc(cbcId)(makeRequest(correctEmail))) shouldBe NotFound
    }

    "return Bad Request if the body of the request does not contain an email" in {
      agentAuthStub(agentAffinityAndEnrolments)
      val badRequest = FakeRequest().withJsonBody(Json.obj("foo" -> "bar"))
      when(mockEisConnector.getCbcSubscription(eqs(cbcId))(any[HeaderCarrier], any[ExecutionContext])).thenReturn(Future.successful(None))

      await(controller.checkKnownFactCbc(cbcId)(badRequest)) shouldBe BadRequest
    }
  }

  "checkKnownFactPillar2" should {
    val clientPlrId = PlrId("XAPLR2222222222")
    val suppliedDate = LocalDate.parse("2001-02-03")
    def makeRequest(registrationDate: LocalDate) = FakeRequest().withJsonBody(Json.obj("registrationDate" -> registrationDate.toString))

    "return Pillar2RecordClientInactive if the Pillar2 record shows the client to be inactive" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientPillar2RegistrationCheckResult(eqs(clientPlrId), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful Pillar2RecordClientInactive)

      await(controller.checkKnownFactPillar2(clientPlrId)(makeRequest(suppliedDate))) shouldBe Pillar2ClientInactive
    }

    "return No Content if PlrId is known in ETMP, the client is active and the effectiveRegistrationDate matched" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientPillar2RegistrationCheckResult(eqs(clientPlrId), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful Pillar2KnownFactCheckOk)

      await(controller.checkKnownFactPillar2(clientPlrId)(makeRequest(suppliedDate))) shouldBe NoContent
    }

    "return Pillar2 Registration Date Does Not Match if PlrId is known in ETMP, client is active and the effectiveRegistrationDate did not match" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientPillar2RegistrationCheckResult(eqs(clientPlrId), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful Pillar2KnownFactNotMatched)

      await(controller.checkKnownFactPillar2(clientPlrId)(makeRequest(suppliedDate))) shouldBe Pillar2RegistrationDateDoesNotMatch
    }

    "return Not Found if PlrId is unknown in ETMP" in {
      agentAuthStub(agentAffinityAndEnrolments)

      when(
        kfcService
          .clientPillar2RegistrationCheckResult(eqs(clientPlrId), eqs(suppliedDate))(any[HeaderCarrier], any[ExecutionContext])
      )
        .thenReturn(Future successful Pillar2DetailsNotFound)

      await(controller.checkKnownFactPillar2(clientPlrId)(makeRequest(suppliedDate))) shouldBe NotFound
    }

    "return BadRequest if body of the message do not contain Registration Date " in {
      agentAuthStub(agentAffinityAndEnrolments)

      await(controller.checkKnownFactPillar2(clientPlrId)(FakeRequest())) shouldBe BadRequest
    }

  }

  "SetRelationshipEnded" should {

    "return No Content when invitation found and updated" in {

      when(
        invitationsService.findInvitationAndEndRelationship(eqs(arn), eqs(nino.value), eqs(Seq(Service.MtdIt)), eqs(Some("Agent")))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future successful true)

      await(
        controller.setRelationshipEnded()(
          FakeRequest()
            .withJsonBody(Json.parse(s"""{
                                        |"arn": "${arn.value}",
                                        |"clientId": "${nino.value}",
                                        |"service": "HMRC-MTD-IT",
                                        |"endedBy": "Agent"
                                        |}""".stripMargin))
        )
      ) shouldBe NoContent
    }

    "return Not Found when no invitation found" in {

      when(
        invitationsService.findInvitationAndEndRelationship(eqs(arn), eqs(nino.value), eqs(Seq(Service.MtdIt)), eqs(Some("Agent")))(
          any[HeaderCarrier],
          any[ExecutionContext]
        )
      ).thenReturn(Future successful false)

      await(
        controller.setRelationshipEnded()(
          FakeRequest()
            .withJsonBody(Json.parse(s"""{
                                        |"arn": "${arn.value}",
                                        |"clientId": "${nino.value}",
                                        |"service": "HMRC-MTD-IT",
                                        |"endedBy": "Agent"
                                        |}""".stripMargin))
        )
      ) shouldBe InvitationNotFound
    }
  }
}
