package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, DateTimeZone, LocalDate}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.InvitationNotFound
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support.{PlatformAnalyticsStubs, TestHalResponseInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AgencyAlternativeItsaControllerISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  override def beforeEach() {
    super.beforeEach()
    await(invitationsRepo.ensureIndexes)
    ()
  }

  "PUT /alt-itsa/update/:nino" should {

    val request = FakeRequest("PUT", "/alt-itsa/update/:nino")

    "return 204 when there are no alternative itsa invitations found for the client" in {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 204
    }

    "return 500 when DES MtdItId call fails leaving alt-itsa invitation untouched" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 500
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when DES MtdItId call return 404 (client not signed up to MtdItId) leaving alt-itsa invitation untouched" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenMtdItIdIsUnknownFor(nino)

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when client has MtdItId, updating the invitation store to replace Nino" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2.copy(clientId = mtdItId))
    }

    "return 201 (Created) when there is PartialAuth invitation and ETMP create relationship call succeeds" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, DateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 500 when there is PartialAuth invitation but ETMP create relationship call fails (Nino will be replaced)" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, DateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationshipFails(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 500
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe PartialAuth
    }

    "return 201 when there is PartialAuth but clientId is MTDITID (as if ETMP create relationship call failed previously)" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, DateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdate(nino)(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }
  }

  "PUT /agent/alt-itsa/update/:arn" should {

    val request = FakeRequest("PUT", "/agent/alt-itsa/update/:arn")

    "return 204 when there are no alt-itsa invitations found for the agent" in {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
    }

    "return 500 when DES MtdItId call fails" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 500
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when a DES MtdItId call returns 404 (a client is not signed up to MtdItId)" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsUnknownFor(nino2)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2)
    }

    "return 204 when MtdItId found and replace Nino on multiple records for the same client" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2.copy(clientId = mtdItId))
    }

    "return 204 when MtdItId found and replace Nino on multiple records for different clients" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val anotherAgent = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino2, mtdItId2)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2.copy(clientId = mtdItId2))
      await(invitationsRepo.findByInvitationId(anotherAgent.invitationId)) shouldBe Some(anotherAgent)
    }

    "return 204 when MtdItId found and create relationship succeeds, updating status to Accepted" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val anotherAgent = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      await(invitationsRepo.update(altItsaPending1, PartialAuth, DateTime.now()))
      await(invitationsRepo.update(altItsaPending2, PartialAuth, DateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino2, mtdItId2)

      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId2)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204

      val modified1 = await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId))
      val modified2 = await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId))
      val anotherArn = await(invitationsRepo.findByInvitationId(anotherAgent.invitationId))

      modified1.get.suppliedClientId shouldBe ClientIdentifier(nino)
      modified1.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified1.get.status shouldBe Accepted

      modified2.get.suppliedClientId shouldBe ClientIdentifier(nino2)
      modified2.get.clientId shouldBe ClientIdentifier(mtdItId2)
      modified2.get.status shouldBe Accepted

      anotherArn.get.status shouldBe Pending
      anotherArn.get.clientId shouldBe ClientIdentifier(nino2)
    }

    "return 500 when create relationship fails for a client" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      await(invitationsRepo.update(altItsaPending1, PartialAuth, DateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)

      givenCreateRelationshipFails(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 500

      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val mongoResult = await(invitationsRepo.findInvitationsBy(arn = Some(arn)))

      mongoResult.count(_.status == PartialAuth) shouldBe 1
    }

    "return 204 when there is PartialAuth but clientId is MTDITID (as if ETMP create relationship call failed previously)" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId2,
          nino2,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      val altItsaPending3 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId3,
          nino3,
          None,
          DateTime.now(),
          LocalDate.now().plusDays(21),
          None)
      )

      await(invitationsRepo.update(altItsaPending1, PartialAuth, DateTime.now()))
      await(invitationsRepo.update(altItsaPending2, PartialAuth, DateTime.now()))
      await(invitationsRepo.update(altItsaPending3, PartialAuth, DateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino2, mtdItId2)
      givenMtdItIdIsKnownFor(nino3, mtdItId3)

      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId2)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId3)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204

      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)
      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId2)
      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId3)

      val modified1 = await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId))
      val modified2 = await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId))
      val modified3 = await(invitationsRepo.findByInvitationId(altItsaPending3.invitationId))

      modified1.get.suppliedClientId shouldBe ClientIdentifier(nino)
      modified1.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified1.get.status shouldBe Accepted

      modified2.get.suppliedClientId shouldBe ClientIdentifier(nino2)
      modified2.get.clientId shouldBe ClientIdentifier(mtdItId2)
      modified2.get.status shouldBe Accepted

      modified3.get.suppliedClientId shouldBe ClientIdentifier(nino3)
      modified3.get.clientId shouldBe ClientIdentifier(mtdItId3)
      modified3.get.status shouldBe Accepted
    }

    "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {

      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel").withHeaders("X-Session-ID" -> "1234")
      val getResult = FakeRequest("GET", "agencies/:arn/invitations/sent/:invitationId")

      s"return 204 when a PartialAuth invitation is successfully cancelled" in {

        val pending: Invitation = await(createInvitation(arn, altItsaClient))
        val partialAuth: Invitation = await(invitationsRepo.update(pending, PartialAuth, DateTime.now()))

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenPlatformAnalyticsRequestSent(true)

        val response = controller.cancelInvitation(arn, partialAuth.invitationId)(request)
        status(response) shouldBe 204

        val updatedInvitation = controller.getSentInvitation(arn, partialAuth.invitationId)(getResult)
        status(updatedInvitation) shouldBe 200

        val invitationStatus = contentAsJson(updatedInvitation).as[TestHalResponseInvitation].status

        invitationStatus shouldBe DeAuthorised.toString

        val event = Event("authorisation request", "deauthorised", altItsaClient.service.id.toLowerCase,
          Seq(DimensionValue(7, altItsaClient.clientType.getOrElse("personal")), DimensionValue(8, partialAuth.invitationId.value), DimensionValue(9, "unknown"), DimensionValue(11, "true")))

        verifySingleEventAnalyticsRequestSent(List(event))
      }



      "return InvitationNotFound when there is no invitation to cancel" in {
        val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)

        val response = controller.cancelInvitation(arn, InvitationId("A7GJRTMY4DS3T"))(request)

        await(response) shouldBe InvitationNotFound
      }
    }
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
}
