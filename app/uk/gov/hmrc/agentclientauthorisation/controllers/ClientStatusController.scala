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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject._
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientStatusController.ClientStatus
import uk.gov.hmrc.agentclientauthorisation.model.{Pending, Service}
import uk.gov.hmrc.agentclientauthorisation.service.{Cache, InvitationsService}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

@Singleton
class ClientStatusController @Inject()(
  invitationsService: InvitationsService,
  relationshipsConnector: RelationshipsConnector,
  clientStatusCache: ClientStatusCache)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, authConnector) {

  implicit val ec: ExecutionContext = ecp.get

  val getStatus: Action[AnyContent] = Action.async { implicit request =>
    withClientIdentifiedBy { identifiers: Seq[(Service, String)] =>
      for {
        status <- if (identifiers.isEmpty) ClientStatusController.defaultClientStatus
                 else
                   clientStatusCache(ClientStatusController.toCacheKey(identifiers)) {
                     for {
                       invitationsInfoList <- Future.sequence(identifiers.map {
                                               case (service, clientId) =>
                                                 invitationsService
                                                   .findInvitationsInfoBy(
                                                     service = Some(service),
                                                     clientId = Some(clientId))
                                             })
                       hasPendingInvitations = invitationsInfoList.exists(_.exists(_.status == Pending))
                       hasInvitationsHistory = invitationsInfoList.exists(_.exists(_.status != Pending))

                       hasExistingAfiRelationships <- relationshipsConnector.getActiveAfiRelationships.map(_.nonEmpty)
                       hasExistingRelationships <- if (hasExistingAfiRelationships) Future.successful(true)
                                                  else relationshipsConnector.getActiveRelationships.map(_.nonEmpty)
                     } yield ClientStatus(hasPendingInvitations, hasInvitationsHistory, hasExistingRelationships)
                   }
      } yield Ok(Json.toJson(status))
    }
  }

}

object ClientStatusController {

  case class ClientStatus(
    hasPendingInvitations: Boolean,
    hasInvitationsHistory: Boolean,
    hasExistingRelationships: Boolean)

  object ClientStatus {
    implicit val formats: OFormat[ClientStatus] = Json.format[ClientStatus]
  }

  def toCacheKey(identifiers: Seq[(Service, String)]): String =
    identifiers
      .sortBy(_._1.enrolmentKey)
      .map(i => s"${i._1}__${i._2}".toLowerCase.replaceAllLiterally(" ", ""))
      .mkString(",")

  val defaultClientStatus = Future.successful(ClientStatus(false, false, false))

}

trait ClientStatusCache extends Cache[ClientStatus]
