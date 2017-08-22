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
import org.mockito.Matchers.{any, eq => eqs}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import play.api.libs.json.JsArray
import play.api.mvc.Result
import play.api.test.FakeRequest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.agentclientauthorisation.support.{AkkaMaterializerSpec, ClientEndpointBehaviours}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

class ClientInvitationsControllerSpec extends AkkaMaterializerSpec with MockitoSugar with BeforeAndAfterEach with ClientEndpointBehaviours {
  private val enrolmentsNotNeededForThisTest = new URL("http://localhost/enrolments-not-specified")

  val controller = new ClientInvitationsController(invitationsService, authConnector)

  val invitationId = BSONObjectID.generate.stringify
  val generator = new Generator()
  val nino = nino1
  val arn: Arn = Arn("12345")


  "Accepting an invitation" should {
    behave like clientStatusChangeEndpoint(nino1)(
      Accepted,
      controller.acceptInvitation(nino1, invitationId),
      whenInvitationIsAccepted
    )

    "not change the invitation status if relationship creation fails" in {
      pending
    }
  }

  "Rejecting an invitation" should {
    behave like clientStatusChangeEndpoint(nino1)(
      Rejected,
      controller.rejectInvitation(nino1, invitationId),
      whenInvitationIsRejected
    )
  }

  "getInvitations" should {
    "return 200 and an empty list when there are no invitations for the client" in {
      //whenAuthIsCalled.thenReturn(Future successful Authority(Some(nino), enrolmentsUrl = enrolmentsNotNeededForThisTest))
      when(invitationsService.translateToMtdItId(
        eqs(nino1.value),eqs("ni"))(any[HeaderCarrier],any[ExecutionContext])).thenReturn(Future successful Some(mtdItId1))
      whenClientReceivedInvitation.thenReturn(Future successful Nil)

      val result: Result = await(controller.getInvitations(nino1, None)(FakeRequest()))
      status(result) shouldBe 200

      (jsonBodyOf(result) \ "_embedded" \ "invitations").get shouldBe JsArray()
    }

    "return 404 when no translation found for supplied client id and type" in {
      when(invitationsService.translateToMtdItId(
        eqs(nino1.value),eqs("ni"))(any[HeaderCarrier],any[ExecutionContext])).thenReturn(Future successful None)

      val result: Result = await(controller.getInvitations(nino1, None)(FakeRequest()))
      status(result) shouldBe 404
    }

    "not include the invitation ID in invitations to encourage HATEOAS API usage" in {
      //whenAuthIsCalled.thenReturn(Future successful Authority(Some(nino), enrolmentsUrl = enrolmentsNotNeededForThisTest))

      when(invitationsService.translateToMtdItId(
        eqs(nino1.value),eqs("ni"))(any[HeaderCarrier],any[ExecutionContext])).thenReturn(Future successful Some(mtdItId1))

      whenClientReceivedInvitation.thenReturn(Future successful List(
        Invitation(
          BSONObjectID("abcdefabcdefabcdefabcdef"), arn, "MTDITID", mtdItId1.value, "postcode", nino1.value, "ni",
          List(StatusChangeEvent(new DateTime(2016, 11, 1, 11, 30), Accepted)))))

      val result: Result = await(controller.getInvitations(nino1, None)(FakeRequest()))
      status(result) shouldBe 200

      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "id").asOpt[String] shouldBe None
      ((jsonBodyOf(result) \ "_embedded" \ "invitations")(0) \ "invitationId").asOpt[String] shouldBe None
    }
  }
}
