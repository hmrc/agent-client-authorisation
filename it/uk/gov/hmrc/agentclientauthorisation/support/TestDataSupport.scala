package uk.gov.hmrc.agentclientauthorisation.support

import org.joda.time.{DateTime, LocalDate}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service.{MtdIt, PersonalIncomeRecord, Trust, Vat}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

import scala.concurrent.{ExecutionContext, Future}

trait TestDataSupport {

  val arn: Arn = Arn("TARN0000001")
  val arn2: Arn = Arn("TARN0000002")
  val arn3: Arn = Arn("JARN3069344")

  val personal = Some("personal")
  val business = Some("business")

  val serviceITSA = "HMRC-MTD-IT"
  val servicePIR = "PERSONAL-INCOME-RECORD"
  val serviceVAT = "HMRC-MTD-VAT"
  val serviceCGT = "HMRC-CGT-PD"

  val nino: Nino = Nino("AB123456A")
  val nino2: Nino = Nino("AB123456B")
  val mtdItId = MtdItId("ABCDEF123456789")
  val mtdItId2 = MtdItId("TUWXYZ123456789")
  val vrn = Vrn("101747696")
  val vrn2 = Vrn("121747696")

  val postcode: String = "AA11AA"
  val vatRegDate: LocalDate = LocalDate.parse("2018-01-01")
  val dateOfBirth: LocalDate = LocalDate.parse("1980-04-10")

  val utr = Utr("2134514321")
  val utr2 = Utr("3087612352")

  val cgtRef = CgtRef("XMCGTP123456789")
  val cgtRef2 = CgtRef("XMCGTP987654321")
  val cgtRefBus = CgtRef("XMCGTP678912345")
  val cgtRefBus2 = CgtRef("XMCGTP345678912")

  val dfe = (clientName: String) => DetailsForEmail("abc@def.com", "Mr Agent", clientName)

  val STRIDE_ROLE = "maintain agent relationships"
  val NEW_STRIDE_ROLE = "maintain_agent_relationships"

  val strideRoles = Seq(STRIDE_ROLE, NEW_STRIDE_ROLE)

  val tpd = TypeOfPersonDetails("Individual", Left(IndividualName("firstName", "lastName")))
  val tpdBus = TypeOfPersonDetails("Organisation", Right(OrganisationName("Trustee")))

  val cgtAddressDetails =
    CgtAddressDetails("line1", Some("line2"), Some("line2"), Some("line2"), "GB", Some("postcode"))

  val cgtSubscription = CgtSubscription("CGT", SubscriptionDetails(tpd, cgtAddressDetails))
  val cgtSubscriptionBus = CgtSubscription("CGT", SubscriptionDetails(tpdBus, cgtAddressDetails))

  case class TestClient[T <: TaxIdentifier](
                         clientType: Option[String],
                         clientName: String,
                         service: Service,
                         clientIdType: ClientIdType[T],
                         urlIdentifier: String,
                         clientId: TaxIdentifier,
                         suppliedClientId: TaxIdentifier,
                         wrongIdentifier: TaxIdentifier)

  val itsaClient = TestClient(personal, "Trade Pears", MtdIt, MtdItIdType, "MTDITID", mtdItId, nino, mtdItId2)
  val irvClient = TestClient(personal, "John Smith", PersonalIncomeRecord, NinoType, "NI", nino, nino, nino2)
  val vatClient = TestClient(personal, "GDT", Vat, VrnType, "VRN", vrn, vrn, vrn2)
  val trustClient = TestClient(business, "Nelson James Trust", Trust, UtrType, "UTR", utr, utr, utr2)
  val cgtClient = TestClient(personal, "firstName lastName", Service.CapitalGains, CgtRefType, "CGTPDRef", cgtRef, cgtRef, cgtRef2)
  val cgtClientBus = TestClient(business, "Trustee", Service.CapitalGains, CgtRefType, "CGTPDRef", cgtRefBus, cgtRefBus, cgtRefBus2)

  val uiClients = List(itsaClient, irvClient, vatClient, trustClient, cgtClient)
  val strideSupportedClient = List(itsaClient, vatClient, trustClient, cgtClient)

  val apiClients = List(itsaClient, vatClient)

  /*
    Note this is just example of Mongo Failures. Not Actual ones for the error messages given
   */
  val testFailedInvitationsRepo = new InvitationsRepository {
    override def create(arn: Arn,
                        clientType: Option[String],
                        service: Service,
                        clientId: ClientId,
                        suppliedClientId: ClientId,
                        detailsForEmail: Option[DetailsForEmail],
                        startDate: DateTime,
                        expiryDate: LocalDate)(implicit ec: ExecutionContext): Future[Invitation] =
      Future failed new Exception ("Unable to Create Invitation")

    override def update(invitation: Invitation,
                        status: InvitationStatus,
                        updateDate: DateTime)(implicit ec: ExecutionContext): Future[Invitation] =
      Future failed new Exception ("Unable to Update Invitation")

    override def findByInvitationId(invitationId: InvitationId)(
      implicit ec: ExecutionContext): Future[Option[Invitation]] =
      Future failed new Exception ("Unable to Find Invitation by ID")

    override def findInvitationsBy(arn: Option[Arn],
                                   services: Seq[Service],
                                   clientId: Option[String],
                                   status: Option[InvitationStatus],
                                   createdOnOrAfter: Option[LocalDate])(implicit ec: ExecutionContext): Future[List[Invitation]] =
      Future failed new Exception("Unable to Find Invitations")

    override def findInvitationInfoBy(arn: Option[Arn],
                                      service: Option[Service],
                                      clientId: Option[String],
                                      status: Option[InvitationStatus],
                                      createdOnOrAfter: Option[LocalDate])(implicit ec: ExecutionContext): Future[List[InvitationInfo]] =
      Future failed new Exception("Unable to Find Invitation Information")

    override def findInvitationInfoBy(arn: Arn,
                                      clientIdTypeAndValues: Seq[(String, String)],
                                      status: Option[InvitationStatus])(implicit ec: ExecutionContext): Future[List[InvitationInfo]] =
      Future failed new Exception("Unable to Find Invitation Information")

    override def refreshAllInvitations(implicit ec: ExecutionContext): Future[Unit] =
      Future failed new Exception("Unable to Find Invitation Information")

    override def refreshInvitation(id: BSONObjectID)(implicit ec: ExecutionContext): Future[Unit] =
      Future failed new Exception("Unable to Refresh Information")

    override def removeEmailDetails(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit] =
      Future failed new Exception("Unable to remove Email Details")

    override def removeAllInvitationsForAgent(arn: Arn)(implicit ec:  ExecutionContext): Future[Int] =
      Future failed new Exception(s"Unable to remove Invitations for ${arn.value}")
  }

  /*
  :Note this is just example of Mongo Failures. Not Actual ones for the error messages given
   */
  val testFailedAgentReferenceRepo = new AgentReferenceRepository {
    override def create(agentReferenceRecord: AgentReferenceRecord)(implicit ec: ExecutionContext): Future[Int] =
      Future failed new Exception("Unable to create Agent Reference Record")

    override def findBy(uid: String)(implicit ec: ExecutionContext): Future[Option[AgentReferenceRecord]] =
      Future failed new Exception("Unable to Find Record by UID")

    override def findByArn(arn: Arn)(implicit ec: ExecutionContext): Future[Option[AgentReferenceRecord]] =
      Future failed new Exception("Unable to Find Record by Arn")

    override def updateAgentName(uid: String, newAgentName: String)(implicit ex: ExecutionContext): Future[Unit] =
      Future failed new Exception("Unable to Update Agent Name")

    override def removeAgentReferencesForGiven(arn: Arn)(implicit ec: ExecutionContext): Future[Int] =
      Future failed new Exception(s"Unable to Remove References for given Agent")
  }
}
