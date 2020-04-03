/*
 * Copyright 2020 HM Revenue & Customs
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
import javax.inject.Inject
import play.api.i18n.{Lang, Langs, MessagesApi}
import play.api.{Logger, LoggerLike}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{DesConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class EmailService @Inject()(
  desConnector: DesConnector,
  clientNameService: ClientNameService,
  emailConnector: EmailConnector,
  invitationsRepository: InvitationsRepository,
  appConfig: AppConfig,
  messagesApi: MessagesApi)(implicit langs: Langs) {

  protected def getLogger: LoggerLike = Logger

  implicit val lang: Lang = langs.availables.head

  def createDetailsForEmail(arn: Arn, clientId: ClientId, service: Service)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[DetailsForEmail] =
    for {
      agencyRecordDetails <- desConnector
                              .getAgencyDetails(arn)
                              .map {
                                case Some(details) => details
                                case _ =>
                                  getLogger.warn(s"Agency Record details not found for: ${arn.value}")
                                  throw AgencyEmailNotFound(arn)
                              }
      agencyName = agencyRecordDetails.agencyDetails
        .flatMap(_.agencyName)
        .getOrElse(throw AgencyNameNotFound(arn))
      agencyEmail = agencyRecordDetails.agencyDetails
        .flatMap(_.agencyEmail)
        .getOrElse(throw AgencyEmailNotFound(arn))

      clientName <- clientNameService.getClientNameByService(clientId.value, service).map {
                     case Some(name) => name
                     case _ =>
                       getLogger.warn(s"Name not found in Client Record for: ${clientId.value} to send email")
                       throw new ClientNameNotFound
                   }
    } yield DetailsForEmail(agencyEmail, agencyName, clientName)

  def sendEmail(invitation: Invitation, templateId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] =
    invitation.detailsForEmail match {
      case Some(dfe) =>
        for {
          _ <- {
            val emailInfo: EmailInformation =
              emailInformation(templateId, dfe.agencyEmail, dfe.agencyName, dfe.clientName, invitation)
            emailConnector
              .sendEmail(emailInfo)
              .recoverWith {
                case e =>
                  getLogger.warn("sending email failed", e)
                  Future.successful(())
              }
          }
          _ <- invitationsRepository.removeEmailDetails(invitation)
        } yield ()
      case _ =>
        Future.successful((): Unit)
    }

  def sendAcceptedEmail(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(invitation, "client_accepted_authorisation_request")

  def sendRejectedEmail(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    sendEmail(invitation, "client_rejected_authorisation_request")

  def sendExpiredEmail(invitation: Invitation)(implicit ec: ExecutionContext): Future[Unit] = {
    implicit val hc: HeaderCarrier = HeaderCarrier(
      extraHeaders = Seq("Expired-Invitation" -> s"${invitation.invitationId.value}"))
    sendEmail(invitation, "client_expired_authorisation_request")
  }

  private def emailInformation(
    templateId: String,
    agencyEmail: String,
    agencyName: String,
    clientName: String,
    invitation: Invitation) =
    EmailInformation(
      Seq(agencyEmail),
      templateId,
      Map(
        "agencyName"   -> agencyName,
        "clientName"   -> clientName,
        "expiryPeriod" -> appConfig.invitationExpiringDuration.toString,
        "service" -> (invitation.service.id match {
          case HMRCMTDIT   => messagesApi(s"service.$HMRCMTDIT")
          case HMRCPIR     => messagesApi(s"service.$HMRCPIR")
          case HMRCMTDVAT  => messagesApi(s"service.$HMRCMTDVAT")
          case HMRCTERSORG => messagesApi(s"service.$HMRCTERSORG")
          case HMRCCGTPD   => messagesApi(s"service.$HMRCCGTPD")
        })
      )
    )

}
