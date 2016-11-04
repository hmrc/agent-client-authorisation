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
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsArray
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.Accounts
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.ClientEndpointBehaviours
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class ClientInvitationsControllerSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with ClientEndpointBehaviours {

  val controller = new ClientInvitationsController(invitationsService, authConnector, agenciesFakeConnector)

  val invitationId = BSONObjectID.generate.stringify
  val clientId = "clientId"
  val saUtr = "saUtr"
  val arn: Arn = Arn("12345")


  "Accepting an invitation" should {
    behave like clientStatusChangeEndpoint(
      controller.acceptInvitation(clientId, invitationId),
      whenInvitationIsAccepted
    )

    "not change the invitation status if relationship creation fails" in {
      pending
    }
  }


  "Rejecting an invitation" should {
    behave like clientStatusChangeEndpoint(
      controller.rejectInvitation(clientId, invitationId),
      whenInvitationIsRejected
    )
  }


  "getInvitations" should {

    "return 200 and an empty list when there are no invitations for the client" in {
      whenAuthIsCalled.thenReturn(Future successful Accounts(None, Some(SaUtr(saUtr))))
      whenMtdClientIsLookedUp.thenReturn(Future successful Some(MtdClientId(clientId)))

      when(invitationsService.clientsReceived("mtd-sa", clientId, None)).thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(clientId, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations") shouldBe JsArray()
    }

    "include the agency URL in invitations" in {
      whenAuthIsCalled.thenReturn(Future successful Accounts(None, Some(SaUtr(saUtr))))
      whenMtdClientIsLookedUp.thenReturn(Future successful Some(MtdClientId(clientId)))

      val expectedUrl = "http://somevalue"
      when(agenciesFakeConnector.agencyUrl(arn)).thenReturn(new URL(expectedUrl))

      when(invitationsService.clientsReceived("mtd-sa", clientId, None)).thenReturn(Future successful List(
        Invitation(BSONObjectID("abcdefabcdefabcdefabcdef"), arn, "mtd-sa", "client id", "postcode", List(
          StatusChangeEvent(new DateTime(2016, 11, 1, 11, 30), Accepted)))))

      val result: Result = await(controller.getInvitations(clientId, None)(FakeRequest()))
      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "_links" \ "agency" \ "href").as[String] shouldBe expectedUrl
    }

    //TODO do we need this?
//    "filter by status when a status is specified" in {
//      whenAuthIsCalled.thenReturn(Future successful Accounts(None, Some(SaUtr(saUtr))))
//      whenMtdClientIsLookedUp.thenReturn(Future successful Some(MtdClientId(clientId)))
//
//      when(invitationsService.list("mtd-sa", clientId, Some(Accepted))).thenReturn(Future successful ...)
//
//      ...
//    }
  }
}
