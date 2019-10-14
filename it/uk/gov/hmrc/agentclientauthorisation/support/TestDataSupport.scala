package uk.gov.hmrc.agentclientauthorisation.support

import org.joda.time.LocalDate
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model.Service.{MtdIt, PersonalIncomeRecord, Trust, Vat}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}

trait TestDataSupport {

  val arn: Arn = Arn("TARN0000001")
  val arn2: Arn = Arn("TARN0000002")

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


}
