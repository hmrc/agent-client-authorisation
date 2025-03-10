/*
 * Copyright 2024 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatusAction.unapply
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl}
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentclientauthorisation.util.DateUtils
import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.{Instant, LocalDateTime, ZoneOffset}
import scala.concurrent.Future

//scalastyle:off
class ClientInvitationsControllerAcrISpec extends BaseISpec with RelationshipStubs with EmailStub with PlatformAnalyticsStubs {

  override protected def additionalConfiguration: Map[String, Any] =
    super.additionalConfiguration + ("acr-mongo-activated" -> true)

  val controller: ClientInvitationsController = app.injector.instanceOf[ClientInvitationsController]
  val repository: InvitationsRepository = app.injector.instanceOf[InvitationsRepositoryImpl]

  def createInvitation[T <: TaxIdentifier](arn: Arn, testClient: TestClient[T], hasEmail: Boolean = true): Invitation = {
    val startDate = Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime
    val expiryDate = startDate.plusSeconds(appConfig.invitationExpiringDuration.toSeconds).toLocalDate

    Invitation.createNew(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if (hasEmail) Some(dfe(testClient.clientName)) else None,
      startDate,
      expiryDate,
      None
    )
  }

  def createACAInvitation[T <: TaxIdentifier](arn: Arn, testClient: TestClient[T], hasEmail: Boolean = true): Future[Invitation] =
    repository.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if (hasEmail) Some(dfe(testClient.clientName)) else None,
      None
    )

  def createEmailInfo(dfe: DetailsForEmail, expiryDate: String, templateId: String, service: Service): EmailInformation = {

    val serviceText = service match {
      case MtdIt                => "manage their Making Tax Digital for Income Tax."
      case MtdItSupp            => "manage their Making Tax Digital for Income Tax."
      case PersonalIncomeRecord => "view their PAYE income record."
      case Vat                  => "manage their Making Tax Digital for VAT."
      case Trust                => "maintain a trust or an estate."
      case TrustNT              => "maintain a trust or an estate."
      case CapitalGains         => "manage their Capital Gains Tax on UK property account."
      case Ppt                  => "manage their Plastic Packaging Tax."
      case Cbc | CbcNonUk       => "manage their Country-by-Country."
      case Pillar2              => "manage their Pillar2."
    }

    EmailInformation(
      Seq(dfe.agencyEmail),
      templateId,
      Map("agencyName" -> s"${dfe.agencyName}", "clientName" -> s"${dfe.clientName}", "expiryDate" -> expiryDate, "service" -> s"$serviceText")
    )
  }

  def givenGroupIdIsKnownForArn(arn: Arn, groupId: String): StrideAuthStubs = {
    stubFor(
      get(urlPathEqualTo(s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-AS-AGENT~AgentReferenceNumber~${arn.value}/groups"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{ "principalGroupIds": [ "$groupId" ] }""")
        )
    )
    this
  }

  def givenFriendlyNameChangeRequestSent(): StrideAuthStubs = {
    stubFor(
      put(urlPathMatching(s"\\/enrolment-store-proxy\\/enrolment-store\\/groups\\/.+\\/enrolments\\/.+\\/friendly_name"))
        .willReturn(
          aResponse()
            .withStatus(200)
          // .withBody(s"""{ "principalGroupIds": [ "$groupId" ] }""")
        )
    )
    this
  }

  def verifyFriendlyNameChangeRequestSent(): Unit =
    eventually {
      verify(
        1,
        putRequestedFor(urlPathMatching(s"\\/enrolment-store-proxy\\/enrolment-store\\/groups\\/.+\\/enrolments\\/.+\\/friendly_name"))
      )
    }

  def givenUtrKnownForCBC(): StrideAuthStubs = {
    stubFor(
      post(urlEqualTo(s"/enrolment-store-proxy/enrolment-store/enrolments"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "service":"HMRC-CBC-ORG",
                         |  "enrolments":[
                         |    {
                         |      "identifiers":[{"key":"cbcId","value":"XECBC8079578736"},{"key":"UTR","value":"8130839560"}],
                         |      "verifiers":[{"key":"Email","value":"m@j4JelAH.eu"}]
                         |      }
                         |   ]
                         |
                         |}
                         |""".stripMargin)
        )
    )
    this
  }

  def verifyUtrKnownForCBCWasSent(): Unit =
    eventually {
      verify(
        1,
        postRequestedFor(
          urlEqualTo(s"/enrolment-store-proxy/enrolment-store/enrolments")
        )
      )
    }

  def givenPptSubscriptionWithName(pptRef: PptRef, isIndividual: Boolean, deregisteredDetailsPresent: Boolean, isDeregistered: Boolean) =
    stubFor(
      get(urlEqualTo(s"/plastic-packaging-tax/subscriptions/PPT/${pptRef.value}/display"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |"legalEntityDetails": {
                         |  "dateOfApplication": "2021-10-12",
                         |  "customerDetails": {
                         |    "customerType": "Individual",
                         |    "individualDetails": {
                         |      "firstName": "Plastics",
                         |      "lastName": "Packaging Ltd"
                         |}
                         |  }
                         | }${if (deregisteredDetailsPresent) {
                          s""",
                 |"changeOfCircumstanceDetails": {
                 | "deregistrationDetails": {
                 |    "deregistrationDate": ${if (isDeregistered) """ "2021-10-01" """ else """ "2050-10-01" """}
                 |  }
                 | }"""
                        } else { """""" }}
                         |}""".stripMargin)
        )
    )

  def givenCbcSubscription(tradingName: String): StrideAuthStubs = {
    stubFor(
      post(urlEqualTo(s"/dac6/dct50d/v1"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{
                         |	"displaySubscriptionForCBCResponse": {
                         |		"responseCommon": {
                         |			"status": "OK",
                         |			"processingDate": "2020-08-09T11:23:45Z"
                         |		},
                         |		"responseDetail": {
                         |			"subscriptionID": "XYCBC2764649410",
                         |			"tradingName": "$tradingName",
                         |			"isGBUser": true,
                         |			"primaryContact": [{
                         |					"email": "abc@def.com",
                         |					"phone": "078803423883",
                         |					"mobile": "078803423883",
                         |					"individual": {
                         |						"lastName": "Taylor",
                         |						"firstName": "Tim"
                         |					}
                         |			}],
                         |			"secondaryContact": [{
                         |				"email": "contact@toolsfortraders.com",
                         |				"organisation": {
                         |					"organisationName": "Tools for Traders Limited"
                         |				}
                         |			}]
                         |		}
                         |	}
                         |}""".stripMargin)
        )
    )
    this
  }

  def verifyQueryKnownFactsRequestSent(): Unit =
    eventually {
      verify(
        1,
        postRequestedFor(urlPathEqualTo(s"/enrolment-store-proxy/enrolment-store/enrolments"))
      )
    }

  class LoggedInUser(forStride: Boolean, forBusiness: Boolean = false, altStride: Boolean = false) {
    if (forStride) {
      if (altStride) {
        givenUserIsAuthenticatedWithStride(ALT_STRIDE_ROLE, "strideId-1234456")
      } else {
        givenUserIsAuthenticatedWithStride(NEW_STRIDE_ROLE, "strideId-1234456")
      }
    } else if (forBusiness)
      givenClientAllBusCgt(cgtRefBus)
    else
      givenClientAll(mtdItId, vrn, nino, utr, urn, cgtRef, pptRef, cbcId, plrId)
  }

  trait AddEmailSupportStub {
    givenGetAgencyDetailsStub(arn, Some("Mr Agent"), Some("abc@def.com"))
    givenNinoForMtdItId(mtdItId, nino)
    hasABusinessPartnerRecord(nino)
    givenCitizenDetailsAreKnownFor(nino.value, "19122019")
    givenClientDetailsForVat(vrn)
    val trustNameJson = """{"trustDetails": {"trustName": "Nelson James Trust"}}"""
    getTrustName(utr.value, response = trustNameJson)
    getTrustName(urn.value, response = trustNameJson)
    val cgtSub = cgtSubscription.copy(subscriptionDetails =
      cgtSubscription.subscriptionDetails.copy(typeOfPersonDetails = TypeOfPersonDetails("Trustee", Right(OrganisationName("some org"))))
    )

    getCgtSubscription(cgtRef, 200, Json.toJson(cgtSubscription).toString())
    getCgtSubscription(cgtRefBus, 200, Json.toJson(cgtSubscriptionBus).toString())

  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/accept" should {
    (altItsaClient :: uiClients).foreach { client =>
      runAcceptInvitationsScenario(client, "UI", false)
    }

    strideSupportedClient.foreach { client =>
      runAcceptInvitationsScenario(client, "UI", true)
    }

    apiClients.foreach { client =>
      runAcceptInvitationsScenario(client, "API", false)
    }

    runAcceptInvitationsScenario(cgtClientBus, "UI", false, true)
    runAcceptInvitationsScenario(cgtClientBus, "UI", true, true)

  }

  def runAcceptInvitationsScenario[T <: TaxIdentifier](
    client: TestClient[T],
    journey: String,
    forStride: Boolean,
    forBusiness: Boolean = false
  ): Unit = {

    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/accept").withHeaders(
      "Authorization" -> "Bearer testtoken"
    )
    val getResult =
      FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/:invitationId").withHeaders("Authorization" -> "Bearer testtoken")

    val acrJson = Json.obj(
      "invitationId"         -> "123",
      "arn"                  -> "TARN0000001",
      "service"              -> "HMRC-MTD-IT-SUPP",
      "clientId"             -> "ABCDEF123456789",
      "clientIdType"         -> "MTDITID",
      "suppliedClientId"     -> "AB123456A",
      "suppliedClientIdType" -> "ni",
      "clientName"           -> "Macrosoft",
      "agencyName"           -> "TestAgent",
      "agencyEmail"          -> "agent@email.com",
      "status"               -> "Accepted",
      "relationshipEndedBy"  -> "Me",
      "clientType"           -> "personal",
      "expiryDate"           -> "2020-01-01",
      "created"              -> "2020-02-02T00:00:00Z",
      "lastUpdated"          -> "2020-03-03T00:00:00Z"
    )

    def acrJson2(invitation: Invitation, clientIdType: String, suppliedClientIdType: String, clientName: String, status: String) = Json.obj(
      "invitationId"         -> "123",
      "arn"                  -> s"${invitation.arn.value}",
      "service"              -> s"${invitation.service.id}",
      "clientId"             -> s"${invitation.clientId.value}",
      "clientIdType"         -> s"$clientIdType",
      "suppliedClientId"     -> s"${invitation.suppliedClientId.value}",
      "suppliedClientIdType" -> s"$suppliedClientIdType",
      "clientName"           -> s"$clientName",
      "agencyName"           -> s"${invitation.detailsForEmail.map(_.agencyName).getOrElse("")}",
      "agencyEmail"          -> s"${invitation.detailsForEmail.map(_.agencyEmail).getOrElse("")}",
      "status"               -> s"$status",
      "relationshipEndedBy"  -> "Me",
      "clientType"           -> "personal",
      "expiryDate"           -> s"2020-01-01",
      "created"              -> "2020-02-02T00:00:00Z",
      "lastUpdated"          -> "2020-03-03T00:00:00Z"
    )

    // Invitation exists, Exists relationship for MTDIT,
    s"accept via $journey ${client.urlIdentifier} ${client.service.id} ${if (client.isAltItsaClient) "(ALT-ITSA) "
      else ""}invitation as expected for ${client.clientId.value} ${if (forStride) "stride" else "client"}" in new LoggedInUser(
      forStride,
      forBusiness
    ) with AddEmailSupportStub {
      val invitation: Invitation = createInvitation(arn, client)
      val emailInfo =
        createEmailInfo(dfe(client.clientName), DateUtils.displayDate(invitation.expiryDate), "client_accepted_authorisation_request", client.service)
      if (!client.isAltItsaClient)
        givenCreateRelationship(arn, client.service.id, if (client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
      givenEmailSent(emailInfo)
      anAfiRelationshipIsCreatedWith(arn, client.clientId)
      givenPlatformAnalyticsRequestSent(true)
      givenGroupIdIsKnownForArn(arn, "some-group-id")

      // TODO WG ACR new stubs
      givenFriendlyNameChangeRequestSent()
      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, client.clientName)
      givenACRChangeStatusByIdSuccess(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Accept))
      stubLookupInvitations(
        expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=Accepted",
        responseStatus = NOT_FOUND,
        responseBody = Json.obj()
      )

      invitation.service match {
        case Service.MtdIt if client.isAltItsaClient =>
          givenCheckRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          stubLookupInvitations(
            expectedQueryParams = s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Partialauth",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          stubLookupInvitations(
            expectedQueryParams = s"?services=${Service.MtdIt.id}&clientIds=${client.clientId.value}&status=Partialauth",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

        case Service.MtdIt =>
          givenCheckRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)

          stubLookupInvitations(
            expectedQueryParams = s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted",
            responseStatus = OK,
            responseBody = Json.arr(acrJson)
          )

          givenACRChangeStatusSuccess(
            arn = arn,
            service = Service.MtdItSupp.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )

        case Service.MtdItSupp =>
          givenCheckRelationship(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          stubLookupInvitations(
            expectedQueryParams =
              s"?arn=${arn.value}&services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

        case Service.Ppt =>
          givenPptSubscriptionWithName(pptRef, true, true, false)

        case Service.Cbc | Service.CbcNonUk =>
          givenCbcSubscription(client.clientName)
          givenUtrKnownForCBC()

        case _ =>

      }

      val result: Result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204
      verifyAnalyticsRequestSent(1)
      verifyEmailRequestWasSentWithEmailInformation(1, emailInfo)

      invitation.service match {
        case Service.MtdIt if client.isAltItsaClient =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(
            s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Partialauth"
          )
          verifyStubLookupInvitationsWasSent(s"?services=${Service.MtdIt.id}&clientIds=${client.clientId.value}&status=Partialauth")
          verifyACRChangeStatusWasNOTSent(arn = arn, service = Service.MtdItSupp.id, clientId = client.suppliedClientId.value)

        case Service.MtdIt =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted")
          verifyStubLookupInvitationsWasSent(s"?services=${Service.MtdIt.id}&clientIds=${client.clientId.value}&status=Accepted")
          verifyACRChangeStatusSent(arn = arn, service = Service.MtdItSupp.id, clientId = client.suppliedClientId.value)
        case Service.MtdItSupp =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipWasSent(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipWasSent(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(
            s"?arn=${arn.value}&services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted"
          )
          verifyACRChangeStatusWasNOTSent(arn = arn, service = client.service.id, clientId = client.suppliedClientId.value)
        case Service.Cbc if invitation.clientId.typeId == CbcIdType.id =>
          verifyFriendlyNameChangeRequestSent()
          verifyQueryKnownFactsRequestSent()
        case Service.PersonalIncomeRecord =>
        case _ =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipNotSent(arn, client.service.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipNotSent(arn, client.service.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=Accepted")
          verifyACRChangeStatusWasNOTSent(arn = arn, service = client.service.id, clientId = client.suppliedClientId.value)

      }
    }

    s"return via $journey bad_request for invalid clientType and clientId: ${client.clientId.value} ${client.urlIdentifier} (${client.service.id}) ${if (client.isAltItsaClient) "(ALT-ITSA)"
      else ""} combination ${if (forStride) "stride" else "client"}" in new LoggedInUser(false, forBusiness) {
      val invitationId = InvitationId("D123456789")
      givenAcrInvitationNotFound(invitationId.value)
      val invalidClient: TestClient[T] = client.copy[T](urlIdentifier = client.urlIdentifier.reverse /* to make invalid */ )
      val result: Result =
        await(controller.acceptInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, invitationId)(request))
      status(result) shouldBe 400
    }

    s"attempting via $journey to accept an ${client.service.id} ${if (client.isAltItsaClient) "(ALT-ITSA)"
      else ""} invitation that does not exist for ${client.clientId.value} logged in ${if (forStride) "stride" else "client"}" in new LoggedInUser(
      forStride,
      forBusiness
    ) {
      givenAcrInvitationNotFound(InvitationId("D123456789").value)
      val result: Future[Result] = controller.acceptInvitation(client.urlIdentifier, client.clientId.value, InvitationId("D123456789"))(request)

      status(result) shouldBe 404
    }

    s"attempting via $journey to accept an ${client.service.id} ${if (client.isAltItsaClient) "(ALT-ITSA)"
      else ""} invitation that is not for the client: ${client.clientId.value} if logged in as ${if (forStride) "stride" else "client"}" in new LoggedInUser(
      forStride,
      forBusiness
    ) {
      val invitation: Invitation = createInvitation(arn, client)
      val invitationId: InvitationId = invitation.invitationId

      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, client.clientName)

      val result: Future[Result] = controller.acceptInvitation(client.urlIdentifier, client.wrongIdentifier.value, invitationId)(request)

      status(result) shouldBe 403

    }

    s"accepting via $journey to accept an ${client.service.id} ${if (client.isAltItsaClient) "(ALT-ITSA)"
      else ""} invitation should mark existing invitations as de-authed for the client: ${client.clientId.value} if logged in as ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(false, forBusiness) with AddEmailSupportStub {

      // Create old invitation for service for arn in ACR
      val oldInvitation: Invitation = createInvitation(arn, client)

      // Create new invitation
      val invitation: Invitation = createInvitation(arn, client)
      val acceptedStatus: InvitationStatus = if (client.isAltItsaClient) PartialAuth else Accepted

      givenEmailSent(
        createEmailInfo(dfe(client.clientName), DateUtils.displayDate(invitation.expiryDate), "client_accepted_authorisation_request", client.service)
      )

      if (!client.isAltItsaClient) {
        givenCreateRelationship(arn, client.service.id, if (client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
        givenClientRelationships(arn, client.service.id)
      }
      anAfiRelationshipIsCreatedWith(arn, client.clientId)
      givenPlatformAnalyticsRequestSent(true)
      givenGroupIdIsKnownForArn(arn, "some-group-id")

      // ACR new stubs
      givenFriendlyNameChangeRequestSent()

      // Find new invitationById
      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, client.clientName)
      // Update status to Accepted for new invitationById
      givenACRChangeStatusByIdSuccess(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Accept))

      invitation.service match {
        case Service.MtdIt =>
          givenCheckRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)

          // Check if they is Supp invitation for that agent
          stubLookupInvitations(
            expectedQueryParams = s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = OK,
            responseBody = Json.arr(
              acrJson2(oldInvitation.copy(service = Service.MtdItSupp), client.clientIdType.id, "ni", client.clientName, acceptedStatus.toString)
            )
          )

          // if there is deauth supp
          givenACRChangeStatusSuccess(
            arn = arn,
            service = Service.MtdItSupp.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )

          // check there is main for another agent
          val oldMainItsaWithOtherAgent: Invitation = createInvitation(arn2, client)
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = OK,
            responseBody = Json.arr(
              acrJson2(oldMainItsaWithOtherAgent, client.clientIdType.id, "ni", client.clientName, acceptedStatus.toString)
            )
          )

          // if there is deauth supp
          givenACRChangeStatusSuccess(
            arn = arn2,
            service = client.service.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )

        case Service.MtdItSupp =>
          givenCheckRelationship(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)

          // Check if they is main for that agent
          stubLookupInvitations(
            expectedQueryParams =
              s"?arn=${arn.value}&services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted",
            responseStatus = OK,
            responseBody = Json.arr(
              acrJson2(oldInvitation.copy(service = Service.MtdIt), client.clientIdType.id, "ni", client.clientName, acceptedStatus.toString)
            )
          )

          // if there is deauth supp
          givenACRChangeStatusSuccess(
            arn = arn,
            service = Service.MtdIt.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )

        case Service.Ppt =>
          givenPptSubscriptionWithName(pptRef, true, true, false)
          // Check is they are other invitations for service and client with status Accept
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = OK,
            responseBody =
              Json.arr(acrJson2(oldInvitation, client.clientIdType.id, client.clientIdType.id, client.clientName, acceptedStatus.toString))
          )

          // If they are Deauth them
          givenACRChangeStatusSuccess(
            arn = arn,
            service = client.service.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )

        case Service.Cbc | Service.CbcNonUk =>
          givenCbcSubscription(client.clientName)
          givenUtrKnownForCBC()

          // Check is they are other invitations for service and client with status Accept
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = OK,
            responseBody =
              Json.arr(acrJson2(oldInvitation, client.clientIdType.id, client.clientIdType.id, client.clientName, acceptedStatus.toString))
          )

          // If they are Deauth them
          givenACRChangeStatusSuccess(
            arn = arn,
            service = client.service.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )

        case _ =>
          // Check is they are other invitations for service and client with status Accept
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = OK,
            responseBody =
              Json.arr(acrJson2(oldInvitation, client.clientIdType.id, client.clientIdType.id, client.clientName, acceptedStatus.toString))
          )

          // If they are Deauth them
          givenACRChangeStatusSuccess(
            arn = arn,
            service = client.service.id,
            clientId = client.suppliedClientId.value,
            changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, Some("Client"))
          )
      }

      // New invitation should be "accepted"
      val result: Result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204

    }

    s"accepting via $journey to accept an ${client.service.id} ${if (client.isAltItsaClient) "(ALT-ITSA)"
      else ""} invitation should mark existing invitations in ACA as de-authed for the client: ${client.clientId.value} if logged in as ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(false, forBusiness) with AddEmailSupportStub {

      val acceptedStatus: InvitationStatus = if (client.isAltItsaClient) PartialAuth else Accepted

      // Create old invitation for service for arn in ACA
      val oldInvitation: Invitation = await(createACAInvitation(arn, client))
      await(repository.update(oldInvitation, acceptedStatus, LocalDateTime.now()))

      // Create new invitation
      val invitation: Invitation = createInvitation(arn, client)

      givenEmailSent(
        createEmailInfo(dfe(client.clientName), DateUtils.displayDate(invitation.expiryDate), "client_accepted_authorisation_request", client.service)
      )

      if (!client.isAltItsaClient) {
        givenCreateRelationship(arn, client.service.id, if (client.urlIdentifier == "UTR") "SAUTR" else client.urlIdentifier, client.clientId)
        givenClientRelationships(arn, client.service.id)
      }
      anAfiRelationshipIsCreatedWith(arn, client.clientId)
      givenPlatformAnalyticsRequestSent(true)
      givenGroupIdIsKnownForArn(arn, "some-group-id")

      // ACR new stubs
      givenFriendlyNameChangeRequestSent()

      // Find new invitationById
      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, client.clientName)
      // Update status to Accepted for new invitationById
      givenACRChangeStatusByIdSuccess(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Accept))

      invitation.service match {
        case Service.MtdIt =>
          givenCheckRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)

          // Check if they is Supp invitation for that agent
          stubLookupInvitations(
            expectedQueryParams = s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          // check there is main for another agent
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          // Used for controller.getInvitations to check status for ACA invitation
          stubLookupInvitations(
            expectedQueryParams =
              s"?services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=$DeAuthorised",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

        case Service.MtdItSupp =>
          givenCheckRelationship(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          givenDeleteRelationship(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)

          // Check if they is main for that agent
          stubLookupInvitations(
            expectedQueryParams =
              s"?arn=${arn.value}&services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          // Used for controller.getInvitations to check status for ACA invitation
          stubLookupInvitations(
            expectedQueryParams =
              s"?services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=$DeAuthorised",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

        case Service.Ppt =>
          givenPptSubscriptionWithName(pptRef, true, true, false)
          // Check is they are other invitations for service and client with status Accept
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          // Used for controller.getInvitations to check status for ACA invitation
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$DeAuthorised",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

        case Service.Cbc | Service.CbcNonUk =>
          givenCbcSubscription(client.clientName)
          givenUtrKnownForCBC()

          // Check is they are other invitations for service and client with status Accept
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          // Used for controller.getInvitations to check status for ACA invitation
          stubLookupInvitations(
            expectedQueryParams =
              s"?services=${Service.Cbc.id}&services=${Service.CbcNonUk.id}&clientIds=${client.clientId.value}&status=$DeAuthorised",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

        case _ =>
          // Check is they are other invitations for service and client with status Accept
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$acceptedStatus",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

          // Used for controller.getInvitations to check status for ACA invitation
          stubLookupInvitations(
            expectedQueryParams = s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$DeAuthorised",
            responseStatus = NOT_FOUND,
            responseBody = Json.obj()
          )

      }

      // New invitation should be "accepted"
      val result: Result = await(controller.acceptInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204

      // Old invitation should be "deauthorised"
      val oldInvitationResult: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(DeAuthorised))(getResult)

      val oldInvitationOpt: Option[TestHalResponseInvitation] =
        (contentAsJson(oldInvitationResult) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      oldInvitationOpt.map(_.status) shouldBe Some(DeAuthorised.toString)

      invitation.service match {
        case Service.MtdIt if client.isAltItsaClient =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(
            s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Partialauth"
          )
          verifyStubLookupInvitationsWasSent(s"?services=${Service.MtdIt.id}&clientIds=${client.clientId.value}&status=Partialauth")
          verifyACRChangeStatusWasNOTSent(arn = arn, service = Service.MtdItSupp.id, clientId = client.suppliedClientId.value)

        case Service.MtdIt =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipWasSent(arn, Service.MtdItSupp.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(s"?arn=${arn.value}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted")
          verifyStubLookupInvitationsWasSent(s"?services=${Service.MtdIt.id}&clientIds=${client.clientId.value}&status=Accepted")
          verifyACRChangeStatusWasNOTSent(arn = arn, service = Service.MtdItSupp.id, clientId = client.suppliedClientId.value)
        case Service.MtdItSupp =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipWasSent(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipWasSent(arn, Service.MtdIt.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(
            s"?arn=${arn.value}&services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=Accepted"
          )
          verifyACRChangeStatusWasNOTSent(arn = arn, service = client.service.id, clientId = client.suppliedClientId.value)
        case Service.Cbc if invitation.clientId.typeId == CbcIdType.id =>
          verifyFriendlyNameChangeRequestSent()
          verifyQueryKnownFactsRequestSent()
        case Service.PersonalIncomeRecord =>
        case _ =>
          verifyFriendlyNameChangeRequestSent()
          verifyCheckRelationshipNotSent(arn, client.service.id, client.urlIdentifier, client.clientId)
          verifyDeleteRelationshipNotSent(arn, client.service.id, client.urlIdentifier, client.clientId)
          verifyStubLookupInvitationsWasSent(s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=Accepted")
          verifyACRChangeStatusWasNOTSent(arn = arn, service = client.service.id, clientId = client.suppliedClientId.value)

      }

    }

  }

  "PUT /clients/:clientIdType/:clientId/invitations/received/:invitationId/reject" should {

    uiClients.foreach { client =>
      runRejectInvitationsScenario(client, "UI", false)
    }

    strideSupportedClient.foreach { client =>
      runRejectInvitationsScenario(client, "UI", true)
    }

    apiClients.foreach { client =>
      runRejectInvitationsScenario(client, "API", false)
    }

    runRejectInvitationsScenario(cgtClientBus, "UI", false, true)
    runRejectInvitationsScenario(cgtClientBus, "UI", true, true)
  }

  def runRejectInvitationsScenario[T <: TaxIdentifier](
    client: TestClient[T],
    journey: String,
    forStride: Boolean,
    forBussiness: Boolean = false
  ): Unit = {
    val request = FakeRequest("PUT", "/clients/:clientIdType/:clientId/invitations/received/:invitationId/reject").withHeaders(
      "Authorization" -> "Bearer testtoken"
    )
    val getResult =
      FakeRequest("GET", "/clients/:clientIdType/:clientId/invitations/:invitationId").withHeaders("Authorization" -> "Bearer testtoken")

    s"reject via $journey ${client.urlIdentifier} ${client.service.id} ACR invitation for ${client.clientId.value} as expected with ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(forStride, forBussiness) with AddEmailSupportStub {
      val invitation: Invitation = createInvitation(arn, client)
      givenEmailSent(
        createEmailInfo(dfe(client.clientName), DateUtils.displayDate(invitation.expiryDate), "client_rejected_authorisation_request", client.service)
      )
      givenPlatformAnalyticsRequestSent(true)
      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, client.clientName)
      givenACRChangeStatusByIdSuccess(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Reject))
      if (invitation.service == Service.Cbc || invitation.service == Service.CbcNonUk)
        givenCbcSubscription(client.clientName)

      if (invitation.service == Service.Ppt)
        givenPptSubscriptionWithName(pptRef, true, true, false)

      val result: Result = await(controller.rejectInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204

      // find + update status
      verifyAcrInvitationFound(invitation.invitationId.value, 2)
      verifyACRChangeStatusByIdSent(invitationId = invitation.invitationId.value, action = unapply(InvitationStatusAction.Reject))
      verifyAnalyticsRequestSent(1)

    }

    s"reject via $journey ${client.urlIdentifier} ${client.service.id} ACA invitation for ${client.clientId.value} as expected with ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(forStride, forBussiness) with AddEmailSupportStub {
      val invitation: Invitation = await(createACAInvitation(arn, client))
      givenEmailSent(
        createEmailInfo(dfe(client.clientName), DateUtils.displayDate(invitation.expiryDate), "client_rejected_authorisation_request", client.service)
      )
      givenPlatformAnalyticsRequestSent(true)
      givenAcrInvitationNotFound(invitation.invitationId.value)

      val result: Result = await(controller.rejectInvitation(client.urlIdentifier, client.clientId.value, invitation.invitationId)(request))
      status(result) shouldBe 204

      val queryParams = invitation.service match {
        case Service.MtdIt | Service.MtdItSupp =>
          s"?services=${Service.MtdIt.id}&services=${Service.MtdItSupp.id}&clientIds=${client.clientId.value}&status=$Rejected"
        case Service.Cbc | Service.CbcNonUk =>
          s"?services=${Service.Cbc.id}&services=${Service.CbcNonUk.id}&clientIds=${client.clientId.value}&status=$Rejected"
        case _ =>
          s"?services=${client.service.id}&clientIds=${client.clientId.value}&status=$Rejected"
      }
      // controller.getInvitations
      stubLookupInvitations(expectedQueryParams = queryParams, responseStatus = NOT_FOUND, responseBody = Json.obj())

      val updatedInvitation: Future[Result] = controller.getInvitations(client.urlIdentifier, client.clientId.value, Some(Rejected))(getResult)
      val testInvitationOpt: Option[TestHalResponseInvitation] =
        (contentAsJson(updatedInvitation) \ "_embedded").as[TestHalResponseInvitations].invitations.headOption
      testInvitationOpt.map(_.status) shouldBe Some(Rejected.toString)
      verifyStubLookupInvitationsWasSent(expectedQueryParams = queryParams)

      verifyAcrInvitationFound(invitation.invitationId.value, 1)
      verifyAnalyticsRequestSent(1)

    }

    s"return via $journey bad_request for invalid clientType and clientId: ${client.clientId.value} (${client.service.id}) combination ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(forStride, forBussiness) {
      val invalidClient: TestClient[T] = client.copy[T](urlIdentifier = client.urlIdentifier.reverse /* to make invalid */ )
      val result: Result =
        await(controller.rejectInvitation(invalidClient.urlIdentifier, invalidClient.clientId.value, InvitationId("D123456789"))(request))
      status(result) shouldBe 400
    }

    s"attempting via $journey to reject an ${client.service.id} invitation that does not exist for ${client.clientId.value} to logged in ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(forStride, forBussiness) {
      val invitationId: InvitationId = InvitationId("D123456789")

      givenAcrInvitationNotFound(invitationId.value)
      val result: Future[Result] = controller.rejectInvitation(client.urlIdentifier, client.clientId.value, invitationId)(request)

      status(result) shouldBe 404
      verifyAcrInvitationFound(invitationId.value)
    }

    s"attempting via $journey to reject an ${client.service.id} invitation that is not for the client: ${client.clientId.value} if logged in as ${if (forStride) "stride"
      else "client"}" in new LoggedInUser(forStride, forBussiness) {
      val invitation: Invitation = createInvitation(arn, client)
      val invitationId: InvitationId = invitation.invitationId

      givenAcrInvitationFound(arn, invitation.invitationId.value, invitation, client.clientName)

      val result: Future[Result] = controller.rejectInvitation(client.urlIdentifier, client.wrongIdentifier.value, invitationId)(request)

      status(result) shouldBe 403
    }
  }

}
