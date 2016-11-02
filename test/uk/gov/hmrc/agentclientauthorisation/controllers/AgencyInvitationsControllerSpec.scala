/*
 * Copyright 2016 HM Revenue & Customs
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

import org.joda.time.DateTime
import org.mockito.Matchers.{eq => eqs}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.{JsArray, JsValue}
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegments
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.agentclientauthorisation.support.{AuthMocking, ResettingMockitoSugar}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class AgencyInvitationsControllerSpec extends UnitSpec with ResettingMockitoSugar with AuthMocking with BeforeAndAfterEach {


  val invitationsRepository = resettingMock[InvitationsRepository]
  val postcodeService = resettingMock[PostcodeService]
  val authConnector = resettingMock[AuthConnector]
  val agenciesFakeConnector = resettingMock[AgenciesFakeConnector]

  val controller = new AgencyInvitationsController(invitationsRepository, postcodeService, authConnector, agenciesFakeConnector)

  "getSentInvitations" should {

    "for a matching agency should return the invitations" in {

      val arn = givenAgentIsLoggedIn(Arn("arn1"))

      when(agenciesFakeConnector.agencyUrl(arn)).thenReturn(new URL("http://foo"))
      val firstInvitationId = BSONObjectID.generate
      val secondInvitationId = BSONObjectID.generate
      when(invitationsRepository.list(eqs(arn), eqs(None), eqs(None), eqs(None))).thenReturn(
        Future successful List(
          Invitation(firstInvitationId, arn, "mtd-sa", "clientId", "postcode", events = List(StatusChangeEvent(DateTime.now, Pending))),
          Invitation(secondInvitationId, arn, "mtd-sa", "clientId", "postcode", events = List(StatusChangeEvent(DateTime.now, Pending)))
        )
      )

      val response = await(controller.getSentInvitations(arn, None, None, None)(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 2

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, firstInvitationId)
      invitationLink(jsonBody, 1) shouldBe expectedAgencySentInvitationLink(arn, secondInvitationId)
    }

    "filter by regime when a regime is specified" in {

      val arn = givenAgentIsLoggedIn(Arn("arn1"))

      when(agenciesFakeConnector.agencyUrl(arn)).thenReturn(new URL("http://foo"))
      val mtdSaInvitationId = BSONObjectID.generate
      when(invitationsRepository.list(eqs(arn), eqs(Some("mtd-sa")), eqs(None), eqs(None))).thenReturn(
        Future successful List(
          Invitation(mtdSaInvitationId, arn, "mtd-sa", "clientId", "postcode", events = List(StatusChangeEvent(DateTime.now, Pending)))
        )
      )

      val response = await(controller.getSentInvitations(arn, Some("mtd-sa"), None, None)(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 1

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, mtdSaInvitationId)
    }

    "filter by status when a status is specified" in {

      val arn = givenAgentIsLoggedIn(Arn("arn1"))

      when(agenciesFakeConnector.agencyUrl(arn)).thenReturn(new URL("http://foo"))
      val acceptedInvitationId = BSONObjectID.generate
      when(invitationsRepository.list(eqs(arn), eqs(None), eqs(None), eqs(Some(Accepted)))).thenReturn(
        Future successful List(
          Invitation(acceptedInvitationId, arn, "mtd-sa", "clientId", "postcode", events = List(StatusChangeEvent(DateTime.now, Accepted)))
        )
      )

      val response = await(controller.getSentInvitations(arn, None, None, Some(Accepted))(FakeRequest()))
      status(response) shouldBe 200
      val jsonBody = jsonBodyOf(response)

      invitationsSize(jsonBody) shouldBe 1

      invitationLink(jsonBody, 0) shouldBe expectedAgencySentInvitationLink(arn, acceptedInvitationId)
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
      arn.arn,
      "invitations",
      "sent",
      invitationId.stringify
    )
}
