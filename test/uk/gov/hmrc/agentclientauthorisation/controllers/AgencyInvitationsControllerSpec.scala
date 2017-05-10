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

import java.net.URL

import org.joda.time.DateTime.now
import org.mockito.Matchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsArray, JsValue}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegments
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService}
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, AuthMocking, ResettingMockitoSugar, TransitionInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Generator

import scala.concurrent.Future

class AgencyInvitationsControllerSpec extends AkkaMaterializerSpec with ResettingMockitoSugar with AuthMocking with BeforeAndAfterEach with TransitionInvitation {

  val postcodeService = resettingMock[PostcodeService]
  val invitationsService = resettingMock[InvitationsService]
  val generator = new Generator()
  val authConnector = resettingMock[AuthConnector]

  val controller = new AgencyInvitationsController(postcodeService, invitationsService, authConnector)

  val arn = Arn("arn1")
  val mtdSaPendingInvitationId = BSONObjectID.generate
  val mtdSaAcceptedInvitationId = BSONObjectID.generate
  val otherRegimePendingInvitationId = BSONObjectID.generate

  override protected def beforeEach() = {
    super.beforeEach()

    givenAgentIsLoggedIn(arn)

    val allInvitations = List(
      Invitation(mtdSaPendingInvitationId, arn, "HMRC-MTD-IT", "clientId", "postcode", events = List(StatusChangeEvent(now(), Pending))),
      Invitation(mtdSaAcceptedInvitationId, arn, "HMRC-MTD-IT", "clientId", "postcode", events = List(StatusChangeEvent(now(), Accepted))),
      Invitation(otherRegimePendingInvitationId, arn, "mtd-other", "clientId", "postcode", events = List(StatusChangeEvent(now(), Pending)))
    )

    when(invitationsService.agencySent(eqs(arn), eqs(None), eqs(None), eqs(None), eqs(None))).thenReturn(
      Future successful allInvitations
    )

    when(invitationsService.agencySent(eqs(arn), eqs(Some("HMRC-MTD-IT")), eqs(None), eqs(None), eqs(None))).thenReturn(
      Future successful allInvitations.filter(_.service == "HMRC-MTD-IT")
    )

    when(invitationsService.agencySent(eqs(arn), eqs(None), eqs(None), eqs(None), eqs(Some(Accepted)))).thenReturn(
      Future successful allInvitations.filter(_.status == Accepted)
    )

    when(invitationsService.agencySent(eqs(arn), eqs(Some("HMRC-MTD-IT")), eqs(Some("ni")), any[Option[String]], eqs(Some(Accepted)))).thenReturn(
      Future successful allInvitations.filter(_.status == Accepted)
    )
  }

  "getSentInvitations" should {

    "for a matching agency should return the invitations" in {

      val response = await(controller.getSentInvitations(arn, None, None, None, None)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 3

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaPendingInvitationId)
      invitationLink(jsonBody, 1) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
      invitationLink(jsonBody, 2) shouldBe expectedAgencySentInvitationLink(arn, otherRegimePendingInvitationId)
    }

    "filter by service when a service is specified" in {

      val response = await(controller.getSentInvitations(arn, Some("HMRC-MTD-IT"), None, None, None)(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 2

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaPendingInvitationId)
      invitationLink(jsonBody, 1) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
    }

    "filter by status when a status is specified" in {

      val response = await(controller.getSentInvitations(arn, None, None, None, Some(Accepted))(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 1

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaAcceptedInvitationId)
    }

    "not include the invitation ID in invitations to encourage HATEOAS API usage" in {
      val response = await(controller.getSentInvitations(arn, None, None, None, None)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 3

      (embeddedInvitations(jsonBody)(0) \ "status").asOpt[String] should not be None
      (embeddedInvitations(jsonBody)(0) \ "id").asOpt[String] shouldBe None
      (embeddedInvitations(jsonBody)(0) \ "invitationId").asOpt[String] shouldBe None
    }

    "include all query parameters in the self link" in {
      val service = Some("HMRC-MTD-IT")
      val clientIdType = Some("ni")
      val clientId = Some("AA123456A")
      val invitationStatus = Some(Accepted)
      val response = await(controller.getSentInvitations(arn, service, clientIdType, clientId, invitationStatus)(FakeRequest()))

      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      (jsonBody \ "_links" \ "self" \ "href").as[String] shouldBe routes.AgencyInvitationsController.getSentInvitations(arn, service, clientIdType, clientId, invitationStatus).url
    }

  }

  "cancelInvitation" should {
    "cancel a pending invitation" in {
      val invitation = anInvitation()
      val cancelledInvitation = transitionInvitation(invitation, Cancelled)

      whenAnInvitationIsCancelled thenReturn (Future successful Right(cancelledInvitation))
      whenFindingAnInvitation thenReturn (Future successful Some(invitation))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))

      status(response) shouldBe 204
    }

    "not cancel an already cancelled invitation" in {
      whenAnInvitationIsCancelled thenReturn (Future successful Left("message"))
      whenFindingAnInvitation thenReturn aFutureOptionInvitation()

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))
      response shouldBe invalidInvitationStatus("message")
    }

    "return 403 NO_PERMISSION_ON_AGENCY if the invitation belongs to a different agency" in {
      whenFindingAnInvitation thenReturn aFutureOptionInvitation()

      val response = await(controller.cancelInvitation(new Arn("1234"), mtdSaPendingInvitationId.stringify)(FakeRequest()))

      response shouldBe NoPermissionOnAgency
    }

    "return 403 NO_PERMISSION_ON_AGENCY when the ARN in the invitation is not the same as the ARN in the URL" in {
      whenFindingAnInvitation thenReturn aFutureOptionInvitation(Arn("a-different-arn"))

      val response = await(controller.cancelInvitation(arn, mtdSaPendingInvitationId.stringify)(FakeRequest()))
      response shouldBe NoPermissionOnAgency
    }

    "return 404 if the invitation doesn't exist" in {
      whenFindingAnInvitation thenReturn (Future successful None)

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
//      "agent-client-authorisation",
      "agencies",
      arn.value,
      "invitations",
      "sent",
      invitationId.stringify
    )

  private def anInvitation(arn: Arn = arn) =
    Invitation(mtdSaPendingInvitationId, arn, "HMRC-MTD-IT", "clientId", "postcode", events = List(StatusChangeEvent(now(), Pending)))

  private def aFutureOptionInvitation(arn: Arn = arn) =
    Future successful Some(anInvitation(arn))

  private def whenFindingAnInvitation() = when(invitationsService.findInvitation(any[String]))

  private def whenAnInvitationIsCancelled = when(invitationsService.cancelInvitation(any[Invitation]))
}
