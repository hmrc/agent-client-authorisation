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
import javax.inject.Inject
import play.api.{Logger, LoggerLike}
import play.api.i18n.MessagesApi
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{EmailInformation, Invitation, Service}
import uk.gov.hmrc.http.HeaderCarrier

import scala.collection.Seq
import scala.concurrent.{ExecutionContext, Future}

class EmailService @Inject()(
  asaConnector: AgentServicesAccountConnector,
  clientNameService: ClientNameService,
  emailConnector: EmailConnector,
  messagesApi: MessagesApi) {

  protected def getLogger: LoggerLike = Logger

  private def sendEmail(invitation: Invitation, templateId: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Unit] =
    for {
      agencyEmail <- asaConnector.getAgencyEmailBy(invitation.arn)
      agencyName  <- asaConnector.getAgencyNameViaClient(invitation.arn)
      clientName  <- clientNameService.getClientNameByService(invitation.clientId.value, invitation.service)
      emailInfo = emailInformation(templateId, agencyEmail, agencyName, clientName, invitation)
      result <- emailConnector.sendEmail(emailInfo).recoverWith {
                 case e => {
                   getLogger.error("sending email failed", e)
                   Future.successful(())
                 }
               }
    } yield result

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
    agencyName: Option[String],
    clientName: Option[String],
    invitation: Invitation) =
    EmailInformation(
      Seq(agencyEmail),
      templateId,
      Map(
        "agencyName" -> agencyName.getOrElse(""),
        "clientName" -> clientName.getOrElse(""),
        "service" -> (invitation.service.id match {
          case Service.HMRCMTDIT  => messagesApi(s"service.${Service.HMRCMTDIT}")
          case Service.HMRCPIR    => messagesApi(s"service.${Service.HMRCPIR}")
          case Service.HMRCMTDVAT => messagesApi(s"service.${Service.HMRCMTDVAT}")
        })
      )
    )

}
