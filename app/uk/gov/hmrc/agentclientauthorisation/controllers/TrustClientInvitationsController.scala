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
import javax.inject.{Inject, Named, Provider}
import play.api.mvc.{Action, AnyContent, Request, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.service.InvitationsService
import uk.gov.hmrc.agentmtdidentifiers.model.{InvitationId, MtdItId, Utr}
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class TrustClientInvitationsController @Inject()(invitationsService: InvitationsService)(
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

  def acceptInvitation(utr: Utr, invitationId: InvitationId): Action[AnyContent] =
    AuthorisedClientOrStrideUser(utr, strideRoles) { implicit request => implicit currentUser =>
      implicit val authTaxId: Option[ClientIdentifier[Utr]] =
        if (currentUser.credentials.providerType == "GovernmentGateway")
          Some(ClientIdentifier(UtrType.createUnderlying(utr.value)))
        else None
      acceptInvitation(ClientIdentifier(utr), invitationId)
    }

//TODO Replace With UTR in APB-3937
//  def rejectInvitation(mtdItId: MtdItId, invitationId: InvitationId): Action[AnyContent] =
//    AuthorisedClientOrStrideUser(mtdItId, strideRoles) { implicit request => implicit currentUser =>
//      implicit val authTaxId: Option[ClientIdentifier[MtdItId]] =
//        if (currentUser.credentials.providerType == "GovernmentGateway")
//          Some(ClientIdentifier(MtdItIdType.createUnderlying(mtdItId.value)))
//        else None
//      actionInvitation(ClientIdentifier(mtdItId), invitationId, invitationsService.rejectInvitation)
//    }

}
