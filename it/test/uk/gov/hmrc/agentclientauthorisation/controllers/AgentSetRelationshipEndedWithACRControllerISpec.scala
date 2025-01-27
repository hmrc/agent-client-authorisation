/*
 * Copyright 2018 HM Revenue & Customs
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

import org.apache.pekko.stream.Materializer
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.InvitationNotFound
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.TaxIdentifier

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

class AgentSetRelationshipEndedWithACRControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  override protected def additionalConfiguration: Map[String, Any] =
    super.additionalConfiguration + ("acr-mongo-activated" -> true)

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
    ()
  }

  def createInvitation(arn: Arn, testClient: TestClient[_], hasEmail: Boolean = true): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if (hasEmail) Some(dfe(testClient.clientName)) else None,
      None
    )

  trait StubSetup {
    givenAuditConnector()
    givenAuthorisedAsAgent(arn)
    givenGetAgencyDetailsStub(arn)
  }

  def runSuccessfulSetRelationshipEnded[T <: TaxIdentifier](testClient: TestClient[T]): Unit = {
    val request = FakeRequest("PUT", "/invitations/set-relationship-ended")

    s"return 204 when an ${testClient.service} invitation is successfully updated in ACR" in new StubSetup {

      givenACRChangeStatusSuccess(
        arn = arn,
        service = testClient.service.id,
        clientId = testClient.clientId.value,
        changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, None)
      )

      val payload = SetRelationshipEndedPayload(arn, testClient.clientId.value, testClient.service.id, None)

      val response = controller.setRelationshipEnded()(request.withJsonBody(Json.toJson(payload)))

      status(response) shouldBe 204

      verifyACRChangeStatusSent(arn, testClient.service.id, testClient.clientId.value)
    }
  }

  "PUT /invitations/set-relationship-ended" should {

    uiClients.foreach { client =>
      runSuccessfulSetRelationshipEnded(client)
    }
  }

  "return status 404 when invitation not found in ACR and ACA" in {
    val request = FakeRequest("PUT", "/invitations/set-relationship-ended")
    givenAuditConnector()

    givenACRChangeStatusNotFound(
      arn = arn,
      clientId = "AB123456A",
      service = "HMRC-MTD-IT",
      changeInvitationStatusRequest = ChangeInvitationStatusRequest(DeAuthorised, None)
    )

    val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(s"""{
                                                                                        |"arn": "${arn.value}",
                                                                                        |"clientId": "AB123456A",
                                                                                        |"service": "HMRC-MTD-IT"
                                                                                        |}""".stripMargin)))

    await(response) shouldBe InvitationNotFound
  }

  "return status 400 Bad Request when clientId is incorrect" in {
    val request = FakeRequest("PUT", "/invitations/set-relationship-ended")

    givenACRChangeStatusBadRequest(arn = arn, clientId = "FAKE", service = "HMRC-MTD-IT")

    givenAuditConnector()

    val response = controller.setRelationshipEnded()(request.withJsonBody(Json.parse(s"""{
                                                                                        |"arn": "${arn.value}",
                                                                                        |"clientId": "FAKE",
                                                                                        |"service": "HMRC-MTD-IT"
                                                                                        |}""".stripMargin)))

    status(response) shouldBe 400
  }

}
