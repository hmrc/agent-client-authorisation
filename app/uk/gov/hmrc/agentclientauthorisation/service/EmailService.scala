/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.agentclientauthorisation.service

import cats.data.EitherT._
import cats.instances.future._
import javax.inject.Inject
import play.api.i18n.{Lang, Langs, MessagesApi}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.util.DateUtils
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier
import play.api.Logging

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class EmailService @Inject()(
  desConnector: DesConnector,
  clientNameService: ClientNameService,
  emailConnector: EmailConnector,
  messagesApi: MessagesApi)(implicit langs: Langs)
    extends Logging {

  def createDetailsForEmail(arn: Arn, clientId: ClientId, service: Service)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[DetailsForEmail] = {
    val detailsForEmail = for {
      agencyRecordDetails <- fromOptionF(desConnector.getAgencyDetails(arn), AgencyEmailNotFound(arn))
      agencyName          <- fromOption[Future](agencyRecordDetails.agencyDetails.flatMap(_.agencyName), AgencyNameNotFound(arn))
      agencyEmail         <- fromOption[Future](agencyRecordDetails.agencyDetails.flatMap(_.agencyEmail), AgencyEmailNotFound(arn))
      clientName          <- fromOptionF[Future, Exception, String](clientNameService.getClientNameByService(clientId.value, service), ClientNameNotFound())
    } yield DetailsForEmail(agencyEmail, agencyName, clientName)
    detailsForEmail.leftMap { ex =>
      getLogger.error(s"createDetailsForEmail error: ${ex.getMessage}", ex); throw ex
    }.merge
  }

  implicit val lang: Lang = langs.availables.head

  protected def getLogger: LoggerLike = Logger

  def sendAcceptedEmail(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(invitation, "client_accepted_authorisation_request")

  def sendRejectedEmail(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(invitation, "client_rejected_authorisation_request")

  def sendExpiredEmail(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq("Expired-Invitation" -> s"${invitation.invitationId.value}"))
    sendEmail(invitation, "client_expired_authorisation_request")
  }

  def sendWarningToExpire(invitations: List[Invitation])(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier(extraHeaders = Seq("Warning-aboutToExpire-email-size" -> s"${invitations.size}"))
    invitations match {
      case Nil => logger info ("empty list - no warning to expire emails to send"); Future successful (())
      case hd :: _ => {
        logger info (s"sending email for ${invitations.size} invitations warning to expire")
        val detailsForEmail = hd.detailsForEmail.getOrElse(throw new Exception("details for email were unavailable"))
        val numberOfInvitations = invitations.size
        val agencyName = detailsForEmail.agencyName
        val agencyEmail = detailsForEmail.agencyEmail
        val createdDate = hd.firstEvent().time
        val expiryDate = hd.expiryDate
        val templateId = if (invitations.size > 1) "agent_invitations_about_to_expire" else "agent_invitation_about_to_expire_single"
        emailConnector.sendEmail(
          EmailInformation(
            to = Seq(agencyEmail),
            templateId = templateId,
            parameters = Map(
              "agencyName"          -> agencyName,
              "numberOfInvitations" -> numberOfInvitations.toString,
              "createdDate"         -> DateUtils.displayDate(createdDate.toLocalDate),
              "expiryDate"          -> DateUtils.displayDate(expiryDate)
            )
          ))
      }
    }
  }

  def sendEmail(invitation: Invitation, templateId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    invitation.detailsForEmail match {
      case Some(dfe) =>
        val emailInfo: EmailInformation = emailInformation(templateId, dfe.agencyEmail, dfe.agencyName, dfe.clientName, invitation)
        emailConnector.sendEmail(emailInfo)
      case _ => {
        logger.warn(s"email not sent as there were no details for email found in invitation")
        Future.successful((): Unit)
      }
    }

  private def emailInformation(templateId: String, agencyEmail: String, agencyName: String, clientName: String, invitation: Invitation) =
    EmailInformation(
      Seq(agencyEmail),
      templateId,
      Map(
        "agencyName" -> agencyName,
        "clientName" -> clientName,
        "expiryDate" -> DateUtils.displayDate(invitation.expiryDate),
        "service" -> (invitation.service.id match {
          case HMRCMTDIT     => messagesApi(s"service.$HMRCMTDIT")
          case HMRCPIR       => messagesApi(s"service.$HMRCPIR")
          case HMRCMTDVAT    => messagesApi(s"service.$HMRCMTDVAT")
          case HMRCTERSORG   => messagesApi(s"service.$HMRCTERSORG")
          case HMRCTERSNTORG => messagesApi(s"service.$HMRCTERSNTORG")
          case HMRCCGTPD     => messagesApi(s"service.$HMRCCGTPD")
        }),
        "additionalInfo" -> {
          if (isAltItsa(invitation))
            s"You now have 4 months to sign $clientName up to Making Tax Digital for Income Tax."
          else ""
        }
      )
    )

  private def isAltItsa(invitation: Invitation): Boolean =
    invitation.service.id == HMRCMTDIT && invitation.clientId == invitation.suppliedClientId ||
      invitation.events.sortBy(_.time.getMillis)(Ordering.Long.reverse).headOption.map(_.status).contains(PartialAuth)
}
