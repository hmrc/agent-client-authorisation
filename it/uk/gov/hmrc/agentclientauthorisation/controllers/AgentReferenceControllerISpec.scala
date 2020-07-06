package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.test.FakeRequest
import uk.gov.hmrc.agentclientauthorisation.model.Service.Vat
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationInfo, Pending}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentReferenceControllerISpec extends BaseISpec {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat = app.injector.instanceOf[Materializer]

  lazy val controller: AgentReferenceController = app.injector.instanceOf[AgentReferenceController]

  val testClients = List(
    itsaClient,
    irvClient,
    vatClient,
    trustClient,
    cgtClient)

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
    ()
  }

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
      LocalDate.now().plusDays(14),
      None)
  }

  trait TestSetup {
    //givenAuditConnector()
    testClients.foreach(client => await(createInvitation(arn, client)))

  }

  "GET  /clients/invitations/uid/:uid" should {

    val request = FakeRequest("GET", "/clients/invitations/uid/:uid")

    "return invitation info for services that are supported by the client's enrolments - has MTD VAT & IT enrolment" in new TestSetup {

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.insert(agentReferenceRecord))

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-MTD-VAT", "HMRC-MTD-IT"))

      status(response) shouldBe 200

      jsonBodyOf(response).as[List[InvitationInfo]].size shouldBe 4
    }

    "return invitation info for services that are supported by the client's enrolments - has VATDEC-ORG and IR-SA enrolments" in new TestSetup {

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.insert(agentReferenceRecord))

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-VATDEC-ORG", "IR-SA"))

      status(response) shouldBe 200

      jsonBodyOf(response).as[List[InvitationInfo]].size shouldBe 2
    }
  }

}
