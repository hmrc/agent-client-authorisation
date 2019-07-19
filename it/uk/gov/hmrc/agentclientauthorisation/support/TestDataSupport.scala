package uk.gov.hmrc.agentclientauthorisation.support

import org.joda.time.LocalDate
import uk.gov.hmrc.agentclientauthorisation.model.DetailsForEmail
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino

trait TestDataSupport {

  val arn: Arn = Arn("TARN0000001")
  val arn2: Arn = Arn("TARN0000002")

  val personal = Some("personal")
  val business = Some("business")

  val serviceITSA = "HMRC-MTD-IT"
  val servicePIR = "PERSONAL-INCOME-RECORD"
  val serviceVAT = "HMRC-MTD-VAT"

  val nino: Nino = Nino("AB123456A")
  val nino2: Nino = Nino("AB123456B")
  val mtdItId = MtdItId("ABCDEF123456789")
  val vrn = Vrn("101747696")

  val postcode: String = "AA11AA"
  val vatRegDate: LocalDate = LocalDate.parse("2018-01-01")
  val dateOfBirth: LocalDate = LocalDate.parse("1980-04-10")

  val utr = Utr("2134514321")
  val utr2 = Utr("3087612352")


  val dfe = DetailsForEmail("abc@def.com", "Mr Agent", "Mr Client")

  val STRIDE_ROLE = "maintain agent relationships"
  val NEW_STRIDE_ROLE = "maintain_agent_relationships"

  val strideRoles = Seq(STRIDE_ROLE, NEW_STRIDE_ROLE)

}
