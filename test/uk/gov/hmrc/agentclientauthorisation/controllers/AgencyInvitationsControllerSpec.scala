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
import org.mockito.Matchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsArray, JsValue}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.MicroserviceAuthConnector
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegments
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService}
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ResettingMockitoSugar, TestData, TransitionInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core._
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

  val controller = new AgencyInvitationsController(postcodeService, invitationsService)(metrics, microserviceAuthConnector) {
    override val authConnector: PlayAuthConnector = mockPlayAuthConnector
  }

  private def agentAuthStub(returnValue: Future[~[Option[AffinityGroup], Enrolments]]) =
    when(mockPlayAuthConnector.authorise(any(), any[Retrieval[~[Option[AffinityGroup], Enrolments]]]())(any())).thenReturn(returnValue)

  override protected def beforeEach(): Unit = {
    super.beforeEach()

    when(invitationsService.agencySent(eqs(arn), eqs(None), eqs(None), eqs(None), eqs(None))(any())).thenReturn(
      Future successful allInvitations
    )

    when(invitationsService.agencySent(eqs(arn), eqs(Some("HMRC-MTD-IT")), eqs(None), eqs(None), eqs(None))(any())).thenReturn(
      Future successful allInvitations.filter(_.service == "HMRC-MTD-IT")
    )

    when(invitationsService.agencySent(eqs(arn), eqs(None), eqs(None), eqs(None), eqs(Some(Accepted)))(any())).thenReturn(
      Future successful allInvitations.filter(_.status == Accepted)
    )

    when(invitationsService.agencySent(eqs(arn), eqs(Some("HMRC-MTD-IT")), eqs(Some("ni")), any[Option[String]], eqs(Some(Accepted)))(any())).thenReturn(
      Future successful allInvitations.filter(_.status == Accepted)
    )
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

    "filter by service when a service is specified" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val response = await(controller.getSentInvitations(arn, Some("HMRC-MTD-IT"), None, None, None)(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 2

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaPendingInvitationId)
      invitationLink(jsonBody, 1) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
    }

    "filter by status when a status is specified" in {

      agentAuthStub(agentAffinityAndEnrolments)

      val response = await(controller.getSentInvitations(arn, None, None, None, Some(Accepted))(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 1

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
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
      whenFindingAnInvitation()(any()) thenReturn (Future successful Some(invitation))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))

      status(response) shouldBe 204
    }

    "not cancel an already cancelled invitation" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenAnInvitationIsCancelled(any()) thenReturn (Future successful Left("message"))
      whenFindingAnInvitation()(any()) thenReturn aFutureOptionInvitation()

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))
      response shouldBe invalidInvitationStatus("message")
    }

    "return 403 NO_PERMISSION_ON_AGENCY if the invitation belongs to a different agency" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenFindingAnInvitation()(any()) thenReturn aFutureOptionInvitation(new Arn("1234"))

      val response = await(controller.cancelInvitation(new Arn("1234"), mtdSaPendingInvitationId.stringify)(FakeRequest()))

      response shouldBe NoPermissionOnAgency
    }

    "return 403 NO_PERMISSION_ON_AGENCY when the ARN in the invitation is not the same as the ARN in the URL" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenFindingAnInvitation()(any()) thenReturn aFutureOptionInvitation(Arn("a-different-arn"))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))
      response shouldBe NoPermissionOnAgency
    }

    "return 404 if the invitation doesn't exist" in {

      agentAuthStub(agentAffinityAndEnrolments)

      whenFindingAnInvitation()(any()) thenReturn (Future successful None)

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))
      response shouldBe InvitationNotFound
    }

  }

  private def invitationLink(agencyInvitationsSent: JsValue, idx: Int): String =
    (embeddedInvitations(agencyInvitationsSent)(idx) \ "_links" \ "self" \ "href").as[String]

  private def invitationsSize(agencyInvitationsSent: JsValue): Int =
    embeddedInvitations(agencyInvitationsSent).value.size

  private def embeddedInvitations(agencyInvitationsSent: JsValue): JsArray =
    (agencyInvitationsSent \ "_embedded" \ "invitations").as[JsArray]

  private def expectedAgencySentInvitationLink(arn: Arn, invitationId: BSONObjectID) =
    encodePathSegments(
      // TODO I would expect the links to start with "/agent-client-authorisation", however it appears they don't and that is not the focus of what I'm testing at the moment
     // "agent-client-authorisation",
      "agencies",
      arn.value,
      "invitations",
      "sent",
      invitationId.stringify
    )

  private def anInvitation(arn: Arn = arn) =
    Invitation(mtdSaPendingInvitationId, arn, "HMRC-MTD-IT", "clientId", "postcode", "nino1", "ni", events = List(StatusChangeEvent(now(), Pending)))

  private def aFutureOptionInvitation(arn: Arn = arn) =
    Future successful Some(anInvitation(arn))

  private def whenFindingAnInvitation()(implicit ec: ExecutionContext) = when(invitationsService.findInvitation(any[String]))

  private def whenAnInvitationIsCancelled(implicit ec: ExecutionContext) = when(invitationsService.cancelInvitation(any[Invitation]))
}
