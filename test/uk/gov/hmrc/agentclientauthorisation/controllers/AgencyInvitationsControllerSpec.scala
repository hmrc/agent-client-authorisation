/*
 * Copyright 2017 HM Revenue & Customs
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
import org.joda.time.DateTime.now
import org.mockito.ArgumentMatchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsArray, JsValue, Json}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegments
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService, StatusUpdateFailure}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData, TransitionInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId}
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.{AffinityGroup, Enrolments, PlayAuthConnector}
import uk.gov.hmrc.domain.Generator

import scala.concurrent.{ExecutionContext, Future}

class AgencyInvitationsControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with BeforeAndAfterEach with TransitionInvitation with TestData {

  val postcodeService: PostcodeService = resettingMock[PostcodeService]
  val invitationsService: InvitationsService = resettingMock[InvitationsService]
  val generator = new Generator()
  val authConnector: AuthConnector = resettingMock[AuthConnector]
  val metrics: Metrics = resettingMock[Metrics]
  val microserviceAuthConnector: MicroserviceAuthConnector = resettingMock[MicroserviceAuthConnector]
  val mockPlayAuthConnector: PlayAuthConnector = resettingMock[PlayAuthConnector]

  val jsonBody = Json.parse(s"""{"service": "HMRC-MTD-IT", "clientIdType": "ni", "clientId": "$nino1", "clientPostcode": "BN124PJ"}""")

  val controller = new AgencyInvitationsController(postcodeService, invitationsService)(metrics, microserviceAuthConnector) {
    override val authConnector: PlayAuthConnector = mockPlayAuthConnector
  }

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any(), any[ExecutionContext])).thenReturn(returnValue)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(invitationsService.agencySent(eqs(arn), eqs(None), eqs(None), eqs(None), eqs(None))(any())).thenReturn(
      Future successful allInvitations
    )

    when(invitationsService.agencySent(eqs(arn), eqs(Some(Service.MtdIt)), eqs(None), eqs(None), eqs(None))(any())).thenReturn(
      Future successful allInvitations.filter(_.service == "HMRC-MTD-IT")
    )

    when(invitationsService.agencySent(eqs(arn), eqs(None), eqs(None), eqs(None), eqs(Some(Accepted)))(any())).thenReturn(
      Future successful allInvitations.filter(_.status == Accepted)
    )

    when(invitationsService.agencySent(eqs(arn), eqs(Some(Service.MtdIt)), eqs(Some("ni")), any[Option[String]], eqs(Some(Accepted)))(any())).thenReturn(
      Future successful allInvitations.filter(_.status == Accepted)
    )
  }


  "createInvitations" should {
    "create an invitation when given correct Arn" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val inviteCreated = Invitation(mtdSaPendingInvitationDbId, mtdSaPendingInvitationId, arn, Service("HMRC-MTD-IT"), mtdItId1.value, "postcode", nino1.value, "ni", events = List(StatusChangeEvent(now(), Pending)))

      when(postcodeService.clientPostcodeMatches(any[String](), any[String]())(any(), any())).thenReturn(Future successful None)
      when(invitationsService.translateToMtdItId(any[String](), any[String]())(any(), any())).thenReturn(Future successful Some(mtdItId1))
      when(invitationsService.create(any[Arn](), any[Service](), any[MtdItId](), any[String](), any[String](), any[String]())(any())).thenReturn(Future successful inviteCreated)

      val response = await(controller.createInvitation(arn)(FakeRequest().withJsonBody(jsonBody)))

      status(response) shouldBe 201
      response.header.headers.get("Location") shouldBe Some(s"/agencies/arn1/invitations/sent/${mtdSaPendingInvitationId.value}")
    }

    "not create an invitation when given arn is incorrect" in {
      agentAuthStub(agentAffinityAndEnrolments)

      val response = await(controller.createInvitation(new Arn("1234"))(FakeRequest().withJsonBody(jsonBody)))

      status(response) shouldBe 403
    }
  }

  "getSentInvitations" should {

    "for a matching agency should return the invitations" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val response = await(controller.getSentInvitations(arn, None, None, None, None)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 3

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaPendingInvitationId)
      invitationLink(jsonBody, 1) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
      invitationLink(jsonBody, 2) shouldBe expectedAgencySentInvitationLink(arn, otherRegimePendingInvitationId)
    }

    "not include the invitation ID in invitations to encourage HATEOAS API usage" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val response = await(controller.getSentInvitations(arn, None, None, None, None)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 3

      (embeddedInvitations(jsonBody)(0) \ "status").asOpt[String] should not be None
      (embeddedInvitations(jsonBody)(0) \ "id").asOpt[String] shouldBe None
      (embeddedInvitations(jsonBody)(0) \ "invitationId").asOpt[String] shouldBe None
    }

    "include all query parameters in the self link" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val service = Some("HMRC-MTD-IT")
      val clientIdType = Some("ni")
      val clientId = Some("AA123456A")
      val invitationStatus = Some(Accepted)
      val response = await(controller.getSentInvitations(arn, service, clientIdType, clientId, invitationStatus)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      (jsonBody \ "_links" \ "self" \ "href").as[String] shouldBe
        routes.AgencyInvitationsController.getSentInvitations(arn, service, clientIdType, clientId, invitationStatus).url
    }

  }

  "cancelInvitation" should {
    "cancel a pending invitation" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val invitation = anInvitation()
      val cancelledInvitation = transitionInvitation(invitation, Cancelled)

      whenAnInvitationIsCancelled(any()) thenReturn (Future successful Right(cancelledInvitation))
      whenFindingAnInvitation() thenReturn (Future successful Some(invitation))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))

      status(response) shouldBe 204
    }

    "not cancel an already cancelled invitation" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenAnInvitationIsCancelled(any()) thenReturn (Future successful Left(StatusUpdateFailure(Cancelled,"message")))
      whenFindingAnInvitation() thenReturn aFutureOptionInvitation()

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))
      response shouldBe invalidInvitationStatus("message")
    }

    "return 403 NO_PERMISSION_ON_AGENCY if the invitation belongs to a different agency" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenFindingAnInvitation() thenReturn aFutureOptionInvitation(new Arn("1234"))

      val response = await(controller.cancelInvitation(new Arn("1234"), mtdSaPendingInvitationId)(FakeRequest()))

      response shouldBe NoPermissionOnAgency
    }

    "return 403 NO_PERMISSION_ON_AGENCY when the ARN in the invitation is not the same as the ARN in the URL" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenFindingAnInvitation() thenReturn aFutureOptionInvitation(Arn("a-different-arn"))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))
      response shouldBe NoPermissionOnAgency
    }

    "return 404 if the invitation doesn't exist" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenFindingAnInvitation() thenReturn (Future successful None)

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId)(FakeRequest()))
      response shouldBe InvitationNotFound
    }

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
    Invitation(mtdSaPendingInvitationDbId, mtdSaPendingInvitationId, arn, Service("HMRC-MTD-IT"), "clientId", "postcode", "nino1", "ni", events = List(StatusChangeEvent(now(), Pending)))

  private def aFutureOptionInvitation(arn: Arn = arn) =
    Future successful Some(anInvitation(arn))

  private def whenFindingAnInvitation() = when(invitationsService.findInvitation(any[InvitationId])(any(), any(), any()))

  private def whenAnInvitationIsCancelled(implicit ec: ExecutionContext) = when(invitationsService.cancelInvitation(any[Invitation]))
}
