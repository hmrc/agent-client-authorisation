/*
 * Copyright 2019 HM Revenue & Customs
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
import javax.inject.{Inject, Named}
import play.api.{Logger, LoggerLike}
import play.api.i18n.MessagesApi
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgencyNameNotFound, AgentServicesAccountConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class EmailService @Inject()(
  asaConnector: AgentServicesAccountConnector,
  clientNameService: ClientNameService,
  emailConnector: EmailConnector,
  invitationsRepository: InvitationsRepository,
  @Named("invitation.expiryDuration") expiryPeriod: String,
  messagesApi: MessagesApi) {

  protected def getLogger: LoggerLike = Logger

  def createDetailsForEmail(arn: Arn, clientId: ClientId, service: Service)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[DetailsForEmail] =
    for {
      agencyEmail <- asaConnector.getAgencyEmailBy(arn)
      agencyName <- asaConnector.getAgencyNameViaClient(arn).map {
                     case Some(name) => name
                     case _ =>
                       getLogger.warn(s"Name not found in Agent Record for: ${arn.value} to send email")
                       throw new AgencyNameNotFound
                   }
      clientName <- clientNameService.getClientNameByService(clientId.value, service).map {
                     case Some(name) => name
                     case _ =>
                       getLogger.warn(s"Name not found in Client Record for: ${clientId.value} to send email")
                       throw new ClientNameNotFound
                   }
    } yield DetailsForEmail(agencyEmail, agencyName, clientName)

  def updateEmailDetails(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Invitation] =
    invitation.detailsForEmail match {
      case Some(_) => Future successful invitation
      case _ =>
        getLogger.warn(s"Adding Details For Email to ${invitation.invitationId.value}. Status: ${invitation.status}")
        createDetailsForEmail(invitation.arn, invitation.clientId, invitation.service)
          .map(dfe => invitation.copy(detailsForEmail = Some(dfe)))
    }

  //TODO Remove Trust Check in APB-3865
  def sendEmail(invitation: Invitation, templateId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] =
    invitation.detailsForEmail match {
      case Some(dfe) =>
        for {
          _ <- if (invitation.service != Service.Trust) {
                val emailInfo: EmailInformation =
                  emailInformation(templateId, dfe.agencyEmail, dfe.agencyName, dfe.clientName, invitation)

                emailConnector
                  .sendEmail(emailInfo)
                  .recoverWith {
                    case e =>
                      getLogger.warn("sending email failed", e)
                      Future.successful(())
                  }
              } else {
                getLogger.warn("No setup for Trust Email Yet")
                Future successful ()
              }
          _ <- invitationsRepository.removeEmailDetails(invitation)
        } yield ()
      case _ =>
        getLogger.error("No Details For Email to send has been found")
        Future.successful(())
    }

  def sendAcceptedEmail(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    for {
      invite <- updateEmailDetails(invitation)
      result <- sendEmail(invite, "client_accepted_authorisation_request")
    } yield result

  def sendRejectedEmail(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    for {
      invite <- updateEmailDetails(invitation)
      result <- sendEmail(invite, "client_rejected_authorisation_request")
    } yield result

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
        "expiryPeriod" -> expiryPeriod,
        "service" -> (invitation.service.id match {
          case Service.HMRCMTDIT  => messagesApi(s"service.${Service.HMRCMTDIT}")
          case Service.HMRCPIR    => messagesApi(s"service.${Service.HMRCPIR}")
          case Service.HMRCMTDVAT => messagesApi(s"service.${Service.HMRCMTDVAT}")
        })
      )
    )

}
