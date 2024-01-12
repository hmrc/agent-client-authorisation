package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{Invitation, InvitationInfo, PartialAuth}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.{Authorization, HeaderCarrier}

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgentReferenceControllerISpec extends BaseISpec {

  val agentReferenceRepo: MongoAgentReferenceRepository = new MongoAgentReferenceRepository(mongoComponent)
  val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat = app.injector.instanceOf[Materializer]

  override implicit val hc: HeaderCarrier = HeaderCarrier(authorization = Some(Authorization("Bearer XYZ")))

  lazy val controller: AgentReferenceController = app.injector.instanceOf[AgentReferenceController]

  val testClients = List(
    itsaClient,
    irvClient,
    vatClient,
    trustClient,
    cgtClient)

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
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
      LocalDateTime.now(),
      LocalDate.now().plusDays(21),
      None)
  }

  trait TestSetup {
    //givenAuditConnector()
    testClients.foreach(client => await(createInvitation(arn, client)))

  }

  "GET  /clients/invitations/uid/:uid" should {

    val request = FakeRequest("GET", "/clients/invitations/uid/:uid").withHeaders(("Authorization" -> "Bearer testtoken"))

    "return invitation info for services that are supported by the client's enrolments - has MTD VAT & IT enrolment" in new TestSetup {

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.collection.insertOne(agentReferenceRecord).toFuture())

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-MTD-VAT", "HMRC-MTD-IT"))

      status(response) shouldBe 200

      contentAsJson(response).as[List[InvitationInfo]].size shouldBe 4
    }

    "return invitation info for services that are supported by the client's enrolments - has VATDEC-ORG and IT enrolment" in new TestSetup {

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.collection.insertOne(agentReferenceRecord).toFuture())

      val response = controller.getInvitationsInfo("ABCDEFGH", None)(authorisedAsValidClientWithAffinityGroup(request, "HMRC-VATDEC-ORG", "HMRC-MTD-IT"))

      status(response) shouldBe 200

      contentAsJson(response).as[List[InvitationInfo]].size shouldBe 3
    }

    "the service is HMRC-MTD-IT and there are ALT-ITSA invitations make updates if client has MTDITID enrolment and return updated list" in {

      val altItsaInvitation = await(createInvitation(arn, altItsaClient))
     await(invitationsRepo.update(altItsaInvitation, PartialAuth, LocalDateTime.now()))

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.collection.insertOne(agentReferenceRecord).toFuture())

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
      await(invitationsRepo.update(altItsaInvitation, PartialAuth, LocalDateTime.now()))

      val agentReferenceRecord: AgentReferenceRecord =
        AgentReferenceRecord("ABCDEFGH", arn, Seq("stan-lee"))

      await(agentReferenceRepo.collection.insertOne(agentReferenceRecord).toFuture())

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
