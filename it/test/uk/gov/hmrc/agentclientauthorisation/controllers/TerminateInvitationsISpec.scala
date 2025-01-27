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

import org.apache.pekko.stream.Materializer
import org.apache.commons.lang3.RandomStringUtils
import org.mongodb.scala.model.Filters
import play.api.libs.concurrent.Futures
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{CitizenDetailsConnector, DesConnector, EisConnector, IfConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.{genericBadRequest, genericInternalServerError}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository._
import uk.gov.hmrc.agentclientauthorisation.service._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.http.HeaderNames

import java.nio.charset.StandardCharsets.UTF_8
import java.time.{LocalDate, LocalDateTime}
import java.util.Base64
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TerminateInvitationsISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])

  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])
  lazy val controller = app.injector.instanceOf(classOf[AgencyInvitationsController])

  val appConfig = app.injector.instanceOf(classOf[AppConfig])
  val authConnector = app.injector.instanceOf(classOf[AuthConnector])
  val postcodeService = app.injector.instanceOf(classOf[PostcodeService])
  val desConnector = app.injector.instanceOf(classOf[DesConnector])
  val ifConnector = app.injector.instanceOf(classOf[IfConnector])
  val eisConnector = app.injector.instanceOf(classOf[EisConnector])
  val auditService = app.injector.instanceOf(classOf[AuditService])
  val knownFactsCheckService = app.injector.instanceOf(classOf[KnownFactsCheckService])
  val relationshipConnector = app.injector.instanceOf(classOf[RelationshipsConnector])
  val emailService = app.injector.instanceOf(classOf[EmailService])
  val agentCacheProvider = app.injector.instanceOf(classOf[AgentCacheProvider])
  val analyticsService = app.injector.instanceOf[PlatformAnalyticsService]
  val citizenDetailsConnector = app.injector.instanceOf[CitizenDetailsConnector]
  val relationshipsConnector = app.injector.instanceOf[RelationshipsConnector]
  val invitationsTransitionalService = app.injector.instanceOf[InvitationsTransitionalService]

  implicit val futures: Futures = app.injector.instanceOf(classOf[Futures])
  implicit val cc: ControllerComponents = app.injector.instanceOf(classOf[ControllerComponents])

  def agentLinkService(agentReferenceRepository: AgentReferenceRepository) =
    new AgentLinkService(agentReferenceRepository, desConnector, metrics, relationshipConnector, appConfig)

  def testInvitationsService(invitationsRepository: InvitationsRepository, agentReferenceRepository: AgentReferenceRepository) =
    new InvitationsService(
      invitationsRepository,
      relationshipConnector,
      analyticsService,
      desConnector,
      ifConnector,
      emailService,
      auditService,
      appConfig,
      metrics
    )

  def testFailedController(invitationsRepository: InvitationsRepository, agentReferenceRepository: AgentReferenceRepository) =
    new AgencyInvitationsController(
      appConfig,
      postcodeService,
      testInvitationsService(invitationsRepository, agentReferenceRepository),
      knownFactsCheckService,
      agentLinkService(agentReferenceRepository),
      desConnector,
      ifConnector,
      eisConnector,
      authConnector,
      citizenDetailsConnector,
      agentCacheProvider,
      relationshipsConnector,
      invitationsTransitionalService
    )

  val jsonDeletedRecords = Json.toJson[TerminationResponse](
    TerminationResponse(
      Seq(DeletionCount(appConfig.appName, "invitations", uiClients.length), DeletionCount(appConfig.appName, "agent-reference", 1))
    )
  )
  val jsonNoDeletedRecords = Json.toJson[TerminationResponse](
    TerminationResponse(Seq(DeletionCount(appConfig.appName, "invitations", 0), DeletionCount(appConfig.appName, "agent-reference", 0)))
  )

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

  val codetable = "ABCDEFGHJKLMNOPRSTUWXYZ123456789"

  def createReference(arn: Arn, name: String): Future[AgentReferenceRecord] = {
    val uid = RandomStringUtils.random(8, codetable)
    val record = AgentReferenceRecord(uid, arn, Seq(name))
    agentReferenceRepo.create(record).map(_ => record)
  }

  trait TestSetup {
    uiClients.foreach(client => await(createInvitation(arn, client)))
    uiClients.foreach(client => await(createInvitation(arn2, client)))
    createReference(arn, "agent-1")
    createReference(arn2, "agent-2")
    givenAuditConnector()
    givenOnlyStrideStub("caat", "123ABC")
    givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
  }

  def basicAuth(string: String): String = Base64.getEncoder.encodeToString(string.getBytes(UTF_8))

  "/remove-agent-invitations" should {
    val request = FakeRequest("DELETE", "/agent/:arn/terminate").withHeaders(HeaderNames.authorisation -> s"Basic ${basicAuth("username:password")}")

    "return 200 for removing all invitations and references for a particular agent" in new TestSetup {

      await(invitationsRepo.findInvitationsBy(arn = Some(arn), within30Days = appConfig.acrMongoActivated)).size shouldBe uiClients.length
      await(agentReferenceRepo.collection.find(Filters.equal("arn", arn.value)).toFuture()).toList.size shouldBe 1

      val response = controller.removeAllInvitationsAndReferenceForArn(arn)(request)

      status(response) shouldBe 200
      contentAsJson(response).as[JsObject] shouldBe jsonDeletedRecords
      await(invitationsRepo.findInvitationsBy(arn = Some(arn), within30Days = appConfig.acrMongoActivated)).size shouldBe 0
      await(agentReferenceRepo.collection.find(Filters.equal("arn", arn.value)).toFuture()).toList.size shouldBe 0
      await(invitationsRepo.findInvitationsBy(arn = Some(arn2), within30Days = appConfig.acrMongoActivated)).size shouldBe uiClients.length
      await(agentReferenceRepo.collection.find(Filters.equal("arn", arn2.value)).toFuture()).toList.size shouldBe 1
    }

    "return 200 no invitations and references to remove" in {
      val response = controller.removeAllInvitationsAndReferenceForArn(arn)(request)

      status(response) shouldBe 200

      contentAsJson(response).as[JsObject] shouldBe jsonNoDeletedRecords

      await(invitationsRepo.collection.find(Filters.equal("arn", arn.value)).toFuture()).toList.size shouldBe 0
      await(agentReferenceRepo.collection.find(Filters.equal("arn", arn.value)).toFuture()).toList.size shouldBe 0
    }

    "return 400 for invalid arn" in {
      val response = controller.removeAllInvitationsAndReferenceForArn(Arn("MARN01"))(request)

      status(response) shouldBe 400

      contentAsJson(response).as[JsObject] shouldBe contentAsJson(Future.successful(genericBadRequest(s"Invalid Arn given by Stride user: MARN01")))
    }

    /*
      Note: this is just example of Mongo Failures. Not Actual ones for the error messages given
     */
    "return 500 if removing all invitations but failed to remove references" in new TestSetup {
      val response = testFailedController(invitationsRepo, testFailedAgentReferenceRepo).removeAllInvitationsAndReferenceForArn(arn)(request)
      status(response) shouldBe 500
      contentAsJson(response) shouldBe contentAsJson(Future.successful(genericInternalServerError("Unable to Remove References for given Agent")))
    }

    "return 500 if removing all references but failed to remove invitations" in new TestSetup {
      val response = testFailedController(testFailedInvitationsRepo, agentReferenceRepo).removeAllInvitationsAndReferenceForArn(arn)(request)
      status(response) shouldBe 500
      contentAsJson(response) shouldBe contentAsJson(Future.successful(genericInternalServerError("Unable to remove Invitations for TARN0000001")))
    }

    "return 500 if failed to remove all invitations and references" in new TestSetup {
      val response =
        testFailedController(testFailedInvitationsRepo, testFailedAgentReferenceRepo).removeAllInvitationsAndReferenceForArn(arn)(request)
      status(response) shouldBe 500
      contentAsJson(response) shouldBe contentAsJson(Future.successful(genericInternalServerError("Unable to remove Invitations for TARN0000001")))
    }
  }

}
