/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.Logging
import uk.gov.hmrc.agentclientauthorisation.connectors.EnrolmentStoreProxyConnector
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.agentmtdidentifiers.model.{CbcIdType, EnrolmentKey, Identifier, Service, UtrType}
import uk.gov.hmrc.http.HeaderCarrier

import java.net.URLEncoder
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FriendlyNameService @Inject() (enrolmentStoreProxyConnector: EnrolmentStoreProxyConnector) extends Logging {
  /*
    APB-6204: Write the friendly name when a client accepts an invitation
    This does not apply to Personal Income Record.
    This call will warnings but not fail in case of problems, as not to interrupt the invitation flow.
    def updateFriendlyName(invitation: Invitation)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {}
   */
  def updateFriendlyName(invitation: Invitation)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] =
    invitation.service match {
      case Service.PersonalIncomeRecord => Future.successful(())
      case _                            => doUpdateFriendlyName(invitation)
    }

  private def doUpdateFriendlyName(invitation: Invitation)(implicit ec: ExecutionContext, hc: HeaderCarrier): Future[Unit] = {
    val maybeEnrolmentKey: Option[String] =
      Try(EnrolmentKey.enrolmentKey(invitation.service.id, invitation.clientId.value)).toOption // don't fail on errors
    val maybeClientName: Option[String] = invitation.detailsForEmail
      .map(name => URLEncoder.encode(name.clientName, "UTF-8"))
      .filter(_.nonEmpty)

    val enrichedEnrolmentKeyF =
      if (invitation.clientId.typeId == CbcIdType.id && invitation.service == Service.Cbc) {
        enrolmentStoreProxyConnector
          .queryKnownFacts(Service.Cbc, Seq(Identifier(CbcIdType.id, invitation.clientId.value)))
          .recover { case _ =>
            None
          }
          .map(
            _.flatMap(
              _.filter(identifier => identifier.key.toLowerCase == UtrType.id && UtrType.isValid(identifier.value)).headOption
                .flatMap(identifier =>
                  maybeEnrolmentKey
                    .map(enrolmentKey => enrolmentKey + "~" + identifier.toString())
                )
            )
          )
      } else Future.successful(maybeEnrolmentKey)

    (for {
      maybeGroupId <- enrolmentStoreProxyConnector.getPrincipalGroupIdFor(invitation.arn).recover { case _ => None /* don't fail on errors */ }
      maybeEnrichedEnrolmentKey <- enrichedEnrolmentKeyF
    } yield {
      val maybeResultFuture = for {
        enrichedEnrolmentKey <- maybeEnrichedEnrolmentKey
        groupId              <- maybeGroupId
        clientName           <- maybeClientName
      } yield enrolmentStoreProxyConnector.updateEnrolmentFriendlyName(groupId, enrichedEnrolmentKey, clientName)
      maybeResultFuture match {
        case Some(future) =>
          future.transform {
            case Success(()) =>
              logger.info(s"updateFriendlyName succeeded for client ${invitation.clientId}, agent ${invitation.arn}")
              Success(())
            case Failure(e) =>
              logger.warn(s"updateFriendlyName failed due to ES19 error for client ${invitation.clientId}, agent ${invitation.arn}: $e")
              Success(()) // don't fail on errors
          }
        case None =>
          val errors = Seq(
            (maybeEnrolmentKey, s"Error generating enrolment key"),
            (maybeClientName, s"Client name not available"),
            (maybeGroupId, s"Error retrieving agent's group id"),
            (maybeEnrichedEnrolmentKey, s"Error enriching enrolment key with ${UtrType.id.toUpperCase()} for ${Service.HMRCCBCORG} service")
          ).collect { case (None, msg) =>
            msg
          }
          val maybeErrorMessage: Option[String] =
            if (errors.isEmpty) None
            else
              Some(s"updateFriendlyName not attempted for client ${invitation.clientId}, agent ${invitation.arn} due to: " + errors.mkString(", "))
          maybeErrorMessage.foreach(logger.warn(_))
          Future.successful(())
      }
    }).flatten

  }
}
