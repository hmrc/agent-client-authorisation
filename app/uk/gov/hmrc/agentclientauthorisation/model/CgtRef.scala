package uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.domain.TaxIdentifier

case class CgtRef(value: String) extends TaxIdentifier

object CgtRef {

  val cgtRegex = "^X[A-Z]CGTP[0-9]{9}$"

  def isValid(value: String): Boolean = value.matches(cgtRegex)
}

