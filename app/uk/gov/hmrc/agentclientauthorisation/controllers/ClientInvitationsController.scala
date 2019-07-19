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
import javax.inject.{Inject, Named, Provider, Singleton}
import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.InvitationId
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.TaxIdentifier

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

@Singleton
class ClientInvitationsController @Inject()(invitationsService: InvitationsService)(
  implicit
  metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor],
  @Named("old.auth.stride.enrolment") oldStrideRole: String,
  @Named("new.auth.stride.enrolment") newStrideRole: String)
    extends BaseClientInvitationsController(invitationsService, metrics, authConnector, auditService) {

  implicit val ec: ExecutionContext = ecp.get

  private val strideRoles = Seq(oldStrideRole, newStrideRole)

  def getInvitation(service: String, identifier: String, invitationId: InvitationId): Action[AnyContent] =
    Action.async {
      Future.successful(NotImplemented)
    }

  def getInvitations(service: String, identifier: String, status: Option[InvitationStatus]): Action[AnyContent] =
    AuthorisedClientOrStrideUser(service, identifier, strideRoles) { implicit request => implicit currentUser =>
      implicit val authTaxId: Option[ClientIdentifier[TaxIdentifier]] =
        if (currentUser.credentials.providerType == "GovernmentGateway")
          Some(ClientIdentifier(currentUser.taxIdentifier))
        else None
      getInvitations(currentUser.service, ClientIdentifier(currentUser.taxIdentifier), status)
    }

  override protected def agencyLink(invitation: Invitation): Option[String] = None
}
