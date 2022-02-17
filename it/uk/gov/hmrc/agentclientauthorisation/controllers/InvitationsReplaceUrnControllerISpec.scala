package uk.gov.hmrc.agentclientauthorisation.controllers

import akka.stream.Materializer
import org.joda.time.{DateTime, LocalDate}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.PlatformAnalyticsStubs
import uk.gov.hmrc.agentmtdidentifiers.model.{ClientIdentifier, Service}

import scala.concurrent.ExecutionContext.Implicits.global

class InvitationsReplaceUrnControllerISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
    await(invitationsRepo.ensureIndexes)
    ()
  }

  "POST /invitations/:urn/replace/utr/:utr  " should {

    "return 404 when there is no invitation to replace urn" in {

      val request = FakeRequest("PUT", "/invitations/:urn/replace/utr/:utr")
      givenAuditConnector()

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(request))

      status(response) shouldBe 404
    }

    "return 201" in {

      val request = FakeRequest("PUT", "/invitations/:urn/replace/utr/:utr")
      givenAuditConnector()
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      getTrustName(utr.value, 200, """{"trustDetails": {"trustName": "Nelson James Trust"}}""")

      val invitation = Invitation.createNew(
        arn,
        Some("personal"),
        Service.TrustNT,
        ClientIdentifier(urn),
        ClientIdentifier(urn),
        None,
        DateTime.now,
        LocalDate.now,
        None
      )
      await(invitationsRepo.insert(invitation))

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(request))

      status(response) shouldBe 201
      val utrInvitation = await(invitationsRepo.find("clientId" -> utr.value))

      utrInvitation.size shouldBe 1
    }
  }

}
