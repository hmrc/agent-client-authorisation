package uk.gov.hmrc.agentclientauthorisation.scenarios

import org.scalatest._
import org.scalatest.concurrent.Eventually
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.{mtdItId1, nino1}
import uk.gov.hmrc.domain.{AgentCode, Nino}

class ClientGetsInvitationByIdApiPlatformISpec extends FeatureSpec with ScenarioHelpers with GivenWhenThen with Matchers with MongoAppAndStubs with Inspectors with Inside with Eventually {

  implicit val arn = RandomArn()
  private implicit val agentCode = AgentCode("LMNOP123456")
  val nino: Nino = nextNino

  feature("Clients can get invitation by Id") {

    scenario(s"for ${Service.MtdIt.id}") {
      val agency = new AgencyApi(this, arn, port)
      val client = new ClientApi(this, nino, mtdItId1, port)

      Given("An agent and a client are logged in")
      given().client(clientId = nino, canonicalClientId = mtdItId1)
        .hasABusinessPartnerRecordWithMtdItId(mtdItId1).aRelationshipIsCreatedWith(arn)
      given().agentAdmin(arn).isLoggedInAndIsSubscribed

      When("An agent sends an invitation")
      val agentInvitationUrl = agency.sendInvitation(client.suppliedClientId, service = MtdItService)
      val id = agentInvitationUrl.split('/').last

      Then(s"the Client should be able to get invitation using invitationId")
      given().client(clientId = nino, canonicalClientId = mtdItId1).isLoggedInWithMtdEnrolment

    }

  }

  private def clientRejectsSecondInvitation(client: ClientApi): Unit = {
    val invitations = client.getInvitations(Seq("status" -> "pending"))
    client.rejectInvitation(invitations.firstInvitation)
    val refetchedInvitations = client.getInvitations()
    refetchedInvitations.firstInvitation.status shouldBe "Accepted"
    refetchedInvitations.secondInvitation.status shouldBe "Rejected"
  }

  private def clientFiltersByRejected(client: ClientApi): Unit = {
    val pendingFiltered = client.getInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 0

    val rejectedFiltered = client.getInvitations(filteredBy = Seq("status" -> "rejected"))
    rejectedFiltered.numberOfInvitations shouldBe 1
    rejectedFiltered.firstInvitation.status shouldBe "Rejected"
  }

  private def clientFiltersByAccepted(client: ClientApi): Unit = {
    val pendingFiltered = client.getInvitations(filteredBy = Seq("status" -> "pending"))
    pendingFiltered.numberOfInvitations shouldBe 1
    pendingFiltered.firstInvitation.status shouldBe "Pending"

    val acceptedFiltered = client.getInvitations(filteredBy = Seq("status" -> "accepted"))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status shouldBe "Accepted"
  }

  private def clientFiltersByMultipleStatuses(client: ClientApi): Unit = {
    val acceptedFiltered = client.getInvitations(filteredBy = Seq("status" -> "accepted", "status" -> "rejected"))
    acceptedFiltered.numberOfInvitations shouldBe 1
    acceptedFiltered.firstInvitation.status shouldBe "Accepted"

    val rejectedFiltered = client.getInvitations(filteredBy = Seq("status" -> "rejected", "status" -> "accepted"))
    rejectedFiltered.numberOfInvitations shouldBe 1
    rejectedFiltered.firstInvitation.status shouldBe "Rejected"
  }

}
