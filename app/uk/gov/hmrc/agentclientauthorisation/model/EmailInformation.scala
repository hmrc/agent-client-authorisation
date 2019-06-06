package uk.gov.hmrc.agentclientauthorisation.model
import play.api.libs.json.Json

case class EmailInformation(to: Seq[String], templateId: String, parameters: Map[String, String], force: Boolean = false, eventUrl: Option[String] = None, onSendUrl: Option[String] = None)


object EmailInformation {
  implicit val formats = Json.format[EmailInformation]
}