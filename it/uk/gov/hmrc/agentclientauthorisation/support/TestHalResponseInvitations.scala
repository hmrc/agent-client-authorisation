package uk.gov.hmrc.agentclientauthorisation.support
import play.api.libs.json._
import uk.gov.hmrc.agentclientauthorisation.model.DetailsForEmail
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import play.api.libs.functional.syntax._


case class TestHalResponseInvitations(invitations: List[TestHalResponseInvitation])

object TestHalResponseInvitations {
  implicit val format: OFormat[TestHalResponseInvitations] = Json.format[TestHalResponseInvitations]
}

case class TestHalResponseInvitation(href: String,
                                     invitationId: InvitationId,
                                     arn: String,
                                     service: String,
                                     clientType: String,
                                     clientId: String,
                                     clientIdType: String,
                                     suppliedClientId: String,
                                     suppliedClientIdType: String,
                                     status: String,
                                     detailsForEmail: Option[DetailsForEmail],
                                     clientActionUrl: Option[String],
                                     created: String,
                                     expiryDate: String,
                                     lastUpdated: String) {

}

object TestHalResponseInvitation {

  implicit val reads: Reads[TestHalResponseInvitation] = {
    ((JsPath \ "_links" \ "self" \ "href").read[String] and
      (JsPath \ "invitationId").read[String] and
      (JsPath \ "arn").read[String] and
      (JsPath \ "service").read[String] and
      (JsPath \ "clientType").read[String] and
      (JsPath \ "clientIdType").read[String] and
      (JsPath \ "clientId").read[String].map(_.replaceAll(" ", "")) and
      (JsPath \ "suppliedClientIdType").read[String] and
      (JsPath \ "suppliedClientId").read[String].map(_.replaceAll(" ", "")) and
      (JsPath \ "status").read[String] and
      (JsPath \ "detailsForEmail").readNullable[DetailsForEmail] and
      (JsPath \ "clientActionUrl").readNullable[String] and
      (JsPath \ "created").read[String] and
      (JsPath \ "expiryDate").read[String] and
      (JsPath \ "lastUpdated").read[String]
      )((href, invitationId, arn, service, clientType, clientIdType, clientId, suppliedClientIdType, suppliedClientId, status, detailsForEmail, clientActionUrl, created, expiryDate, lastUpdated) =>
    TestHalResponseInvitation(href, InvitationId(invitationId), arn, service, clientType, clientIdType, clientId, suppliedClientIdType, suppliedClientId, status, detailsForEmail, clientActionUrl, created, expiryDate, lastUpdated))
    }

  implicit val writes: Writes[TestHalResponseInvitation] = new Writes[TestHalResponseInvitation] {
    override def writes(o: TestHalResponseInvitation)
      : JsValue = Json.obj(
      "_links" -> Json.obj("self" -> Json.obj("href" -> o.href)),
      "invitationId" -> o.invitationId.value,
      "arn" -> o.arn,
      "service"      -> o.service,
      "clientType"   -> o.clientType,
      "clientIdType" -> o.clientIdType,
      "clientId"     -> o.clientId.replaceAll(" ", ""),
      "suppliedClientIdType" -> o.suppliedClientIdType,
      "suppliedClientIdType" -> o.suppliedClientId.replaceAll(" ", ""),
      "status" -> o.status,
      "detailsForEmail" -> o.detailsForEmail,
      "clientActionUrl" -> o.clientActionUrl,
      "created" -> o.created,
      "expiryDate" -> o.expiryDate,
      "lastUpdated" -> o.lastUpdated
    )
  }
}
