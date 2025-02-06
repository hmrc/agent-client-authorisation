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

package uk.gov.hmrc.agentclientauthorisation.controllers

import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientStatusController.ClientStatus
import uk.gov.hmrc.agentclientauthorisation.model.{PartialAuth, Pending}
import uk.gov.hmrc.agentclientauthorisation.service.{AgentCacheProvider, InvitationsService}
import uk.gov.hmrc.agentmtdidentifiers.model.Service
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ClientStatusController @Inject() (
  invitationsService: InvitationsService,
  relationshipsConnector: RelationshipsConnector,
  agentCacheProvider: AgentCacheProvider,
  appConfig: AppConfig
)(implicit metrics: Metrics, cc: ControllerComponents, authConnector: AuthConnector, ec: ExecutionContext)
    extends AuthActions(metrics, appConfig, authConnector, cc) {

  private val clientStatusCache = agentCacheProvider.clientStatusCache

  val getStatus: Action[AnyContent] = Action.async { implicit request =>
    withClientIdentifiedBy {
      case Nil => Future.successful(Ok(Json.toJson(ClientStatus())))
      case identifiers if appConfig.acrMongoActivated =>
        clientStatusCache(ClientStatusController.toCacheKey(identifiers)) {
          for {
            ClientStatus(acrPending, acrHistory, acrRelationships) <- relationshipsConnector.getCustomerStatus
            nonSuspendedInvitationInfoList <-
              if (!acrPending || !acrHistory || !acrRelationships) invitationsService.getNonSuspendedInvitations(identifiers)
              else Future.successful(Nil)
            hasPendingInvitations = acrPending || nonSuspendedInvitationInfoList.exists(_.exists(_.status == Pending))
            hasInvitationsHistory = acrHistory || nonSuspendedInvitationInfoList.exists(_.exists(_.status != Pending))
            hasExistingRelationships = acrRelationships || nonSuspendedInvitationInfoList.exists(_.exists(_.status == PartialAuth))
          } yield ClientStatus(hasPendingInvitations, hasInvitationsHistory, hasExistingRelationships)
        } map { status => Ok(Json.toJson(status)) }
      case identifiers =>
        clientStatusCache(ClientStatusController.toCacheKey(identifiers)) {
          for {
            nonSuspendedInvitationInfoList <- invitationsService.getNonSuspendedInvitations(identifiers)
            hasPendingInvitations = nonSuspendedInvitationInfoList.exists(_.exists(_.status == Pending))
            hasInvitationsHistory = nonSuspendedInvitationInfoList.exists(_.exists(_.status != Pending))
            hasPartialAuthRelationships = nonSuspendedInvitationInfoList.exists(_.exists(_.status == PartialAuth))
            hasExistingAfiRelationships <- relationshipsConnector.getActiveAfiRelationships
                                             .map(_.fold(false)(_.nonEmpty))
            hasExistingRelationships <- if (hasExistingAfiRelationships || hasPartialAuthRelationships) Future.successful(true)
                                        else
                                          relationshipsConnector.getActiveRelationships.map(_.fold(false)(_.nonEmpty))
          } yield ClientStatus(hasPendingInvitations, hasInvitationsHistory, hasExistingRelationships)
        } map { status => Ok(Json.toJson(status)) }
    } {
      Ok(Json.toJson(ClientStatus()))
    }

  }
}

object ClientStatusController {

  case class ClientStatus(hasPendingInvitations: Boolean = false, hasInvitationsHistory: Boolean = false, hasExistingRelationships: Boolean = false)

  object ClientStatus {
    implicit val formats: OFormat[ClientStatus] = Json.format[ClientStatus]
  }

  def toCacheKey(identifiers: Seq[(Service, String)]): String =
    identifiers
      .sortBy(_._1.enrolmentKey)
      .map(i => s"${i._1}__${i._2}".toLowerCase.replace(" ", ""))
      .mkString(",")
}
