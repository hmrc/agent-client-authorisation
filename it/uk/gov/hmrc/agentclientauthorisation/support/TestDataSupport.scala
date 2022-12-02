package uk.gov.hmrc.agentclientauthorisation.support

import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, AgentReferenceRepository, InvitationsRepository}
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.Service.{MtdIt, PersonalIncomeRecord, Ppt, Trust, TrustNT, Vat}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.Future

trait TestDataSupport {

  val arn: Arn = Arn("TARN0000001")
  val arn2: Arn = Arn("TARN0000002")
  val arn3: Arn = Arn("JARN3069344")

  val personal: Option[String] = Some("personal")
  val business: Option[String] = Some("business")

  val serviceITSA = "HMRC-MTD-IT"
  val servicePIR = "PERSONAL-INCOME-RECORD"
  val serviceVAT = "HMRC-MTD-VAT"
  val serviceCGT = "HMRC-CGT-PD"
  val serviceTERS = "HMRC-TERS-ORG"
  val serviceTERSNT = "HMRC-TERSNT-ORG"
  val servicePPT = "HMRC-PPT-ORG"

  val nino: Nino = Nino("AB123456A")
  val nino2: Nino = Nino("AB123456B")
  val nino3: Nino = Nino("AB123456C")
  val nino4: Nino = Nino("AB123456D")
  val nino5: Nino = Nino("AB123457B")
  val nino6: Nino = Nino("AB183456B")
  val mtdItId: MtdItId = MtdItId("ABCDEF123456789")
  val mtdItId2: MtdItId = MtdItId("TUWXYZ123456789")
  val mtdItId3: MtdItId = MtdItId("TBWXYZ123456789")
  val mtdItId4: MtdItId = MtdItId("TUWXYZ123456789")
  val mtdItId5: MtdItId = MtdItId("TIWXYZ123456789")
  val mtdItId6: MtdItId = MtdItId("TOWXYZ123456789")


  val vrn: Vrn = Vrn("101747696")
  val vrn2: Vrn = Vrn("121747696")

  val pptRef: PptRef = PptRef("XAPPT0000000000")
  val pptApplicationDate: LocalDate = LocalDate.parse("2021-10-12")
  val pptDeregistrationDateWhenActive: LocalDate = LocalDate.parse("2050-10-01")
  val pptDeregistrationDateWhenDeregistered: LocalDate = LocalDate.parse("2021-10-01")

  val postcode: String = "AA11AA"
  val vatRegDate: LocalDate = LocalDate.parse("2018-01-01")
  val dateOfBirth: LocalDate = LocalDate.parse("1980-04-10")

  val utr: Utr = Utr("2134514321")
  val utr2: Utr = Utr("3087612352")

  val urn: Urn = Urn("XXTRUST12345678")
  val urn2: Urn = Urn("YYTRUST12345678")

  val cgtRef: CgtRef = CgtRef("XMCGTP123456789")
  val cgtRef2: CgtRef = CgtRef("XMCGTP987654321")
  val cgtRefBus: CgtRef = CgtRef("XMCGTP678912345")
  val cgtRefBus2: CgtRef = CgtRef("XMCGTP345678912")

  val dfe: String => DetailsForEmail = (clientName: String) => DetailsForEmail("abc@def.com", "Mr Agent", clientName)

  val STRIDE_ROLE = "maintain agent relationships"
  val NEW_STRIDE_ROLE = "maintain_agent_relationships"
  val ALT_STRIDE_ROLE = "maintain_agent_manually_assure"

  val strideRoles = Seq(STRIDE_ROLE, NEW_STRIDE_ROLE, ALT_STRIDE_ROLE)

  val tpd: TypeOfPersonDetails = TypeOfPersonDetails("Individual", Left(IndividualName("firstName", "lastName")))
  val tpdBus: TypeOfPersonDetails = TypeOfPersonDetails("Organisation", Right(OrganisationName("Trustee")))

  val cgtAddressDetails: CgtAddressDetails =
    CgtAddressDetails("line1", Some("line2"), Some("line2"), Some("line2"), "GB", Some("postcode"))

  val cgtSubscription: CgtSubscription = CgtSubscription("CGT", SubscriptionDetails(tpd, cgtAddressDetails))
  val cgtSubscriptionBus: CgtSubscription = CgtSubscription("CGT", SubscriptionDetails(tpdBus, cgtAddressDetails))

  case class TestClient[T <: TaxIdentifier](
                         clientType: Option[String],
                         clientName: String,
                         service: Service,
                         clientIdType: ClientIdType[T],
                         urlIdentifier: String,
                         clientId: TaxIdentifier,
                         suppliedClientId: TaxIdentifier,
                         wrongIdentifier: TaxIdentifier) {
    val isAltItsaClient: Boolean = service == MtdIt && clientId == suppliedClientId
  }

  val itsaClient: TestClient[MtdItId] = TestClient(personal, "Trade Pears", MtdIt, MtdItIdType, "MTDITID", mtdItId, nino, mtdItId2)
  val irvClient: TestClient[Nino] = TestClient(personal, "John Smith", PersonalIncomeRecord, NinoType, "NI", nino, nino, nino2)
  val vatClient: TestClient[Vrn] = TestClient(personal, "GDT", Vat, VrnType, "VRN", vrn, vrn, vrn2)
  val trustClient: TestClient[Utr] = TestClient(business, "Nelson James Trust", Trust, UtrType, "UTR", utr, utr, utr2)
  val trustNTClient: TestClient[Urn] = TestClient(business, "Nelson George Trust", TrustNT, UrnType, "URN", urn, urn, urn2)
  val cgtClient: TestClient[CgtRef] = TestClient(personal, "firstName lastName", Service.CapitalGains, CgtRefType, "CGTPDRef", cgtRef, cgtRef, cgtRef2)
  val cgtClientBus: TestClient[CgtRef] = TestClient(business, "Trustee", Service.CapitalGains, CgtRefType, "CGTPDRef", cgtRefBus, cgtRefBus, cgtRefBus2)
  val altItsaClient: TestClient[Nino] = TestClient(personal, "John Smith", MtdIt, NinoType, "MTDITID", nino, nino, nino2)
  val pptClient: TestClient[PptRef] = TestClient(business, "Plastics Packaging Ltd", Ppt, PptRefType, "EtmpRegistrationNumber", pptRef, pptRef, PptRef("XAPPT0000000001") )

  val uiClients = List(itsaClient, irvClient, vatClient, trustClient, trustNTClient, cgtClient, pptClient)
  val strideSupportedClient = List(itsaClient, vatClient, trustClient, cgtClient, pptClient)

  val apiClients = List(itsaClient, vatClient)

  /*
    Note this is just example of Mongo Failures. Not Actual ones for the error messages given
   */
  val testFailedInvitationsRepo: InvitationsRepository = new InvitationsRepository {
    override def create(arn: Arn,
                        clientType: Option[String],
                        service: Service,
                        clientId: ClientId,
                        suppliedClientId: ClientId,
                        detailsForEmail: Option[DetailsForEmail],
                        startDate: LocalDateTime,
                        expiryDate: LocalDate,
                        origin: Option[String]): Future[Invitation] =
      Future failed new Exception ("Unable to Create Invitation")

    override def update(invitation: Invitation,
                        status: InvitationStatus,
                        updateDate: LocalDateTime): Future[Invitation] =
      Future failed new Exception ("Unable to Update Invitation")

    override def setRelationshipEnded(invitation: Invitation, endedBy: String): Future[Invitation] =
      Future failed new Exception ("Unable to set isRelationshipEnded = true")

    override def findByInvitationId(invitationId: InvitationId): Future[Option[Invitation]] =
      Future failed new Exception ("Unable to Find Invitation by ID")

    override def findInvitationsBy(arn: Option[Arn],
                                   services: Seq[Service],
                                   clientId: Option[String],
                                   status: Option[InvitationStatus],
                                   createdOnOrAfter: Option[LocalDate]): Future[List[Invitation]] =
      Future failed new Exception("Unable to Find Invitations")

    override def findInvitationInfoBy(arn: Option[Arn],
                                      service: Option[Service],
                                      clientId: Option[String],
                                      status: Option[InvitationStatus],
                                      createdOnOrAfter: Option[LocalDate]): Future[List[InvitationInfo]] =
      Future failed new Exception("Unable to Find Invitation Information")

    override def findInvitationInfoBy(arn: Arn,
                                      clientIdTypeAndValues: Seq[(String, String, String)],
                                      status: Option[InvitationStatus]): Future[List[InvitationInfo]] =
      Future failed new Exception("Unable to Find Invitation Information")

    override def removePersonalDetails(startDate:LocalDateTime): Future[Unit] =
      Future failed new Exception("Unable to remove Email Details")

    override def removeAllInvitationsForAgent(arn: Arn): Future[Int] =
      Future failed new Exception(s"Unable to remove Invitations for ${arn.value}")

    override def getExpiredInvitationsForGA(expiredWithin: Long): Future[List[Invitation]] =
      Future failed new Exception(s"Unable to retrieve Expired Invitations")

    override def findLatestInvitationByClientId(clientId: String): Future[Option[Invitation]] =
      Future failed new Exception(s"Unable to retrieve latest invitation for a client")

    override def replaceNinoWithMtdItIdFor(invitation: Invitation, mtdItId: MtdItId): Future[Invitation] =
      Future failed new Exception(s"Unable to replace Nino for a client")
  }

  /*
  :Note this is just example of Mongo Failures. Not Actual ones for the error messages given
   */
  val testFailedAgentReferenceRepo: AgentReferenceRepository = new AgentReferenceRepository {
    override def create(agentReferenceRecord: AgentReferenceRecord): Future[Option[String]] =
      Future failed new Exception("Unable to create Agent Reference Record")

    override def findBy(uid: String): Future[Option[AgentReferenceRecord]] =
      Future failed new Exception("Unable to Find Record by UID")

    override def findByArn(arn: Arn): Future[Option[AgentReferenceRecord]] =
      Future failed new Exception("Unable to Find Record by Arn")

    override def updateAgentName(uid: String, newAgentName: String): Future[Unit] =
      Future failed new Exception("Unable to Update Agent Name")

    override def removeAgentReferencesForGiven(arn: Arn): Future[Int] =
      Future failed new Exception(s"Unable to Remove References for given Agent")
  }
}
