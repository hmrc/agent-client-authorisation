/*
 * Copyright 2018 HM Revenue & Customs
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
import org.joda.time.LocalDate
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.AuthActions
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientStatusController.ClientStatus
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

@Singleton
class ClientStatusController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, authConnector) {

  implicit val ec: ExecutionContext = ecp.get

  val getStatus: Action[AnyContent] = Action.async { implicit request =>
    withClientIdentifiedBy { identifiers: Seq[(Service, String)] =>
      val now = LocalDate.now
      for {
        invitationsInfoList <- Future.sequence(identifiers.map {
                                case (service, clientId) =>
                                  invitationsService
                                    .findInvitationsInfoBy(service = Some(service), clientId = Some(clientId))
                              })
        hasPendingInvitations = invitationsInfoList.map(_.exists(_.isPendingOn(now))).foldLeft(false)(_ || _)
        hasInvitationsHistory = invitationsInfoList.map(_.exists(i => !i.isPendingOn(now))).foldLeft(false)(_ || _)
      } yield Ok(Json.toJson(ClientStatus(hasPendingInvitations, hasInvitationsHistory)))
    }
  }

}

object ClientStatusController {

  case class ClientStatus(hasPendingInvitations: Boolean, hasInvitationsHistory: Boolean)

  object ClientStatus {
    implicit val formats: OFormat[ClientStatus] = Json.format[ClientStatus]
  }

}