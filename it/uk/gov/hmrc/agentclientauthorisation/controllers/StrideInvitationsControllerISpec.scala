package uk.gov.hmrc.agentclientauthorisation.controllers
import akka.stream.Materializer
import com.kenshoo.play.metrics.Metrics
import javax.inject.Provider
import org.apache.commons.lang3.RandomStringUtils
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.{ControllerComponents, Result}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.genericServiceUnavailable

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContextExecutor, Future}

class StrideInvitationsControllerISpec extends BaseISpec {

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])
  lazy val controller = app.injector.instanceOf(classOf[StrideInvitationController])

  val appConfig = app.injector.instanceOf(classOf[AppConfig])
  val authConnector = app.injector.instanceOf(classOf[AuthConnector])
  implicit val metrics = app.injector.instanceOf(classOf[Metrics])
  implicit val cc = app.injector.instanceOf(classOf[ControllerComponents])
  implicit val ecp: Provider[ExecutionContextExecutor] = new Provider[ExecutionContextExecutor] {
    override def get(): ExecutionContextExecutor = concurrent.ExecutionContext.Implicits.global
  }

  def testFailedController(invitationsRepository: InvitationsRepository, agentReferenceRepository: AgentReferenceRepository) =
    new StrideInvitationController(appConfig, invitationsRepository, testFailedAgentReferenceRepo, authConnector)

  val jsonDeletedRecords = Json.obj("arn" -> arn.value, "InvitationsDeleted" -> 5, "ReferencesDeleted" -> 1)
  val jsonNoDeletedRecords = Json.obj("arn" -> arn.value, "InvitationsDeleted" -> 0, "ReferencesDeleted" -> 0)

  def createInvitation(arn: Arn,
                       testClient: TestClient[_],
                       hasEmail: Boolean = true): Future[Invitation] = {
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if(hasEmail) Some(dfe(testClient.clientName)) else None,
      DateTime.now(DateTimeZone.UTC),
      LocalDate.now().plusDays(14))
  }

  val codetable = "ABCDEFGHJKLMNOPRSTUWXYZ123456789"

  def createReference(arn:Arn, name: String): Future[AgentReferenceRecord] = {
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

  "/remove-agent-invitations" should {
    val request = FakeRequest("DELETE", "/remove/agent/:arn/records")

    "return 200 for removing all invitations and references for a particular agent" in new TestSetup {

      await(invitationsRepo.count(Json.obj("arn" -> arn.value))) shouldBe 5
      await(agentReferenceRepo.count(Json.obj("arn" -> arn.value))) shouldBe 1

      val response: Result = await(controller.removeAllInvitationsAndReferenceForArn(arn)(request))

      status(response) shouldBe 200
      jsonBodyOf(response).as[JsObject] shouldBe jsonDeletedRecords
      await(invitationsRepo.count(Json.obj("arn" -> arn.value))) shouldBe 0
      await(agentReferenceRepo.count(Json.obj("arn" -> arn.value))) shouldBe 0
      await(invitationsRepo.count(Json.obj("arn" -> arn2.value))) shouldBe 5
      await(agentReferenceRepo.count(Json.obj("arn" -> arn2.value))) shouldBe 1
    }

    "return 200 no invitations and references to remove" in {
      givenOnlyStrideStub("caat", "123ABC")
      val response: Result = await(controller.removeAllInvitationsAndReferenceForArn(arn)(request))

      status(response) shouldBe 200

      jsonBodyOf(response).as[JsObject] shouldBe jsonNoDeletedRecords

      await(invitationsRepo.count(Json.obj("arn" -> arn.value))) shouldBe 0
      await(agentReferenceRepo.count(Json.obj("arn" -> arn.value))) shouldBe 0
    }

    /*
      Note: this is just example of Mongo Failures. Not Actual ones for the error messages given
     */
    "return 503 if removing all invitations but failed to remove references" in new TestSetup {
      val response: Result = await(testFailedController(invitationsRepo, testFailedAgentReferenceRepo).removeAllInvitationsAndReferenceForArn(arn)(request))
      status(response) shouldBe 503
      jsonBodyOf(response) shouldBe jsonBodyOf(genericServiceUnavailable("Unable to Remove References for given Agent"))
    }

    "return 503 if removing all references but failed to remove invitations" in new TestSetup {
      val response: Result = await(testFailedController(testFailedInvitationsRepo, agentReferenceRepo).removeAllInvitationsAndReferenceForArn(arn)(request))
      status(response) shouldBe 503
      jsonBodyOf(response) shouldBe jsonBodyOf(genericServiceUnavailable("Unable to remove Invitations for TARN0000001"))
    }

    "return 503 if failed to remove all invitations and references" in new TestSetup {
      val response: Result = await(testFailedController(testFailedInvitationsRepo, testFailedAgentReferenceRepo).removeAllInvitationsAndReferenceForArn(arn)(request))
      status(response) shouldBe 503
      jsonBodyOf(response) shouldBe jsonBodyOf(genericServiceUnavailable("Unable to remove Invitations for TARN0000001"))
    }
  }

}
