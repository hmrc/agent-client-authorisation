package uk.gov.hmrc.agentclientauthorisation.repository
import org.joda.time.LocalDate
import play.api.libs.json.{Format, Json, Writes}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

object InvitationRecordFormat {

  import play.api.libs.functional.syntax._
  import play.api.libs.json.{JsPath, Reads}

  implicit val serviceFormat = Service.format
  implicit val statusChangeEventFormat = Json.format[StatusChangeEvent]
  implicit val oidFormats = ReactiveMongoFormats.objectIdFormats

  def read(
    id: BSONObjectID,
    invitationId: InvitationId,
    arn: Arn,
    service: Service,
    clientId: String,
    clientIdTypeOp: Option[String],
    suppliedClientId: String,
    suppliedClientIdType: String,
    expiryDateOp: Option[LocalDate],
    events: List[StatusChangeEvent]): Invitation = {

    val expiryDate = expiryDateOp.getOrElse(events.head.time.plusDays(10).toLocalDate)

    val clientIdType = clientIdTypeOp.getOrElse {
      if (Nino.isValid(clientId)) NinoType.id else MtdItIdType.id
    }

    Invitation(
      id,
      invitationId,
      arn,
      service,
      ClientIdentifier(clientId, clientIdType),
      ClientIdentifier(suppliedClientId, suppliedClientIdType),
      expiryDate,
      events
    )
  }

  val reads: Reads[Invitation] = ((JsPath \ "id").read[BSONObjectID] and
    (JsPath \ "invitationId").read[InvitationId] and
    (JsPath \ "arn").read[Arn] and
    (JsPath \ "service").read[Service] and
    (JsPath \ "clientId").read[String] and
    (JsPath \ "clientIdType").readNullable[String] and
    (JsPath \ "suppliedClientId").read[String] and
    (JsPath \ "suppliedClientIdType").read[String] and
    (JsPath \ "expiryDate").readNullable[LocalDate] and
    (JsPath \ "events").read[List[StatusChangeEvent]])(read _)

  val arnClientStateKey = "_arnClientStateKey"

  val writes = new Writes[Invitation] {
    def writes(invitation: Invitation) =
      Json.obj(
        "id"                   -> invitation.id,
        "invitationId"         -> invitation.invitationId,
        "arn"                  -> invitation.arn.value,
        "service"              -> invitation.service.id,
        "clientId"             -> invitation.clientId.value,
        "clientIdType"         -> invitation.clientId.typeId,
        "suppliedClientId"     -> invitation.suppliedClientId.value,
        "suppliedClientIdType" -> invitation.suppliedClientId.typeId,
        "events"               -> invitation.events,
        "expiryDate"           -> invitation.expiryDate,
        arnClientStateKey -> Seq(
          toArnClientStateKey(
            invitation.arn.value,
            invitation.clientId.enrolmentId,
            invitation.clientId.value,
            invitation.mostRecentEvent().status.toString),
          toArnClientStateKey(
            invitation.arn.value,
            invitation.suppliedClientId.enrolmentId,
            invitation.suppliedClientId.value,
            invitation.mostRecentEvent().status.toString)
        )
      )
  }

  def toArnClientStateKey(arn: String, clientIdType: String, clientIdValue: String, status: String): String =
    s"${arn.toLowerCase}~${clientIdType.toLowerCase}~${clientIdValue.toLowerCase}~${status.toLowerCase}"

  val mongoFormat = ReactiveMongoFormats.mongoEntity(Format(reads, writes))
}
