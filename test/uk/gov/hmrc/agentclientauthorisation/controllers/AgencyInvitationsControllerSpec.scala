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
import org.mockito.Matchers._
import org.mockito.Mockito._
import play.api.mvc.{ActionBuilder, Request, Result, Results}
import play.api.test.FakeRequest
import play.api.mvc.{Action, AnyContent, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, Invitation, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.service.PostcodeService
import uk.gov.hmrc.agentclientauthorisation.support.{AuthMocking, ResettingMockitoSugar}
import uk.gov.hmrc.play.test.UnitSpec
import org.mockito.Matchers.{any, eq => eqs}
import reactivemongo.bson.BSONObjectID

import scala.concurrent.Future

class AgencyInvitationsControllerSpec extends UnitSpec with ResettingMockitoSugar with AuthMocking {


  val invitationsRepository = resettingMock[InvitationsRepository]
  val postcodeService = resettingMock[PostcodeService]
  val authConnector = resettingMock[AuthConnector]
  val agenciesFakeConnector = resettingMock[AgenciesFakeConnector]

  val controller = new AgencyInvitationsController(invitationsRepository, postcodeService, authConnector, agenciesFakeConnector)

  "getSentInvitations" should {

    "for a matching agency should return the invitations" in {

      val arn = givenAgentIsLoggedIn(Arn("arn1"))

      when(agenciesFakeConnector.agencyUrl(arn)).thenReturn(new URL("http://foo"))
      when(invitationsRepository.list(eqs(arn), eqs(None), eqs(None), eqs(None))).thenReturn(
        Future successful List(
          Invitation(BSONObjectID.generate, arn, "mtd-sa", "clientId", "postcode", events = List(StatusChangeEvent(DateTime.now, Pending))),
          Invitation(BSONObjectID.generate, arn, "mtd-sa", "clientId", "postcode", events = List(StatusChangeEvent(DateTime.now, Pending)))
        )
      )

      val response = await(controller.getSentInvitations(arn, None, None, None)(FakeRequest()))
      status(response) shouldBe 200
      (jsonBodyOf(response) \ "_links" \ "invitations")
    }
  }
}
