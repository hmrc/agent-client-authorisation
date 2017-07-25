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

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.mockito.Matchers.{eq => eqs, _}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsArray
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.Authority
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ClientEndpointBehaviours}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.{Generator, Nino}

import scala.concurrent.Future

class ClientInvitationsControllerSpec extends AkkaMaterializerSpec with MockitoSugar with BeforeAndAfterEach with ClientEndpointBehaviours {
  private val enrolmentsNotNeededForThisTest = new URL("http://localhost/enrolments-not-specified")

  val controller = new ClientInvitationsController(invitationsService, authConnector)

  val invitationId = BSONObjectID.generate.stringify
  val generator = new Generator()
  val clientId = generator.nextNino.value
  val arn: Arn = Arn("12345")


  "Accepting an invitation" should {
    behave like clientStatusChangeEndpoint(
      Accepted,
      controller.acceptInvitation(clientId, invitationId),
      whenInvitationIsAccepted
    )

    "not change the invitation status if relationship creation fails" in {
      pending
    }
  }


  "Rejecting an invitation" should {
    behave like clientStatusChangeEndpoint(
      Rejected,
      controller.rejectInvitation(clientId, invitationId),
      whenInvitationIsRejected
    )
  }


  "getInvitations" should {

    "return 200 and an empty list when there are no invitations for the client" in {
      whenAuthIsCalled.thenReturn(Future successful Authority(Some(Nino(clientId)), enrolmentsUrl = enrolmentsNotNeededForThisTest))

      whenClientReceivedInvitation.thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(clientId, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations").get shouldBe JsArray()
    }

    "not include the invitation ID in invitations to encourage HATEOAS API usage" in {
      whenAuthIsCalled.thenReturn(Future successful Authority(Some(Nino(clientId)), enrolmentsUrl = enrolmentsNotNeededForThisTest))

      whenClientReceivedInvitation.thenReturn(Future successful List(
        Invitation(BSONObjectID("abcdefabcdefabcdefabcdef"), arn, "mtd-sa", "client id", "postcode", List(
          StatusChangeEvent(new DateTime(2016, 11, 1, 11, 30), Accepted)))))

      val result: Result = await(controller.getInvitations(clientId, None)(FakeRequest()))
      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "id").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "invitationId").asOpt[String] shouldBe None
    }
  }
}
