package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationInfo, PartialAuth}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn

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
      LocalDate.now().plusDays(21),
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

      contentAsJson(response).as[List[InvitationInfo]].size shouldBe 4
    }

    "return invitation info for services that are supported by the client's enrolments - has VATDEC-ORG and IT enrolment" in new TestSetup {

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.insert(agentReferenceRecord))

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-VATDEC-ORG", "HMRC-MTD-IT"))

      status(response) shouldBe 200

      contentAsJson(response).as[List[InvitationInfo]].size shouldBe 3
    }

    "the service is HMRC-MTD-IT and there are ALT-ITSA invitations make updates if client has MTDITID enrolment and return updated list" in {

      val altItsaInvitation = await(createInvitation(arn, altItsaClient))
     await(invitationsRepo.update(altItsaInvitation, PartialAuth, DateTime.now()))

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.insert(agentReferenceRecord))

      await(invitationsRepo.findInvitationInfoBy(arn = Some(arn))).head.isAltItsa shouldBe true

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-VATDEC-ORG", "HMRC-MTD-IT"))

      status(response) shouldBe 200

      val result = contentAsJson(response).as[List[InvitationInfo]]
      result.size shouldBe 1
      result.head.isAltItsa shouldBe false
    }

    "the service is HMRC-MTD-IT and there are ALT-ITSA invitations to update but create relationship fails" in {

      val altItsaInvitation = await(createInvitation(arn, altItsaClient))
      await(invitationsRepo.update(altItsaInvitation, PartialAuth, DateTime.now()))

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.insert(agentReferenceRecord))

      await(invitationsRepo.findInvitationInfoBy(arn = Some(arn))).head.isAltItsa shouldBe true

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationshipFails(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-VATDEC-ORG", "HMRC-MTD-IT"))

      status(response) shouldBe 200

      val result = contentAsJson(response).as[List[InvitationInfo]]
      result.size shouldBe 1
      result.head.isAltItsa shouldBe true
    }
  }

}
