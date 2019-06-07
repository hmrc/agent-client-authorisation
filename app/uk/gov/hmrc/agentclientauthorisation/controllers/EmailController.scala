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
import com.google.inject.Provider
import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsError, JsNull, JsSuccess, JsValue}
import play.api.mvc.{Action, AnyContent, Result}
import uk.gov.hmrc.agentclientauthorisation.audit.AuditService
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, EmailConnector}
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.auth.core.AuthConnector

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class EmailController @Inject()(emailConnector: EmailConnector)(
  implicit metrics: Metrics,
  authConnector: AuthConnector,
  auditService: AuditService,
  ecp: Provider[ExecutionContextExecutor])
    extends AuthActions(metrics, authConnector) {

  implicit val ec: ExecutionContext = ecp.get

  def sendEmail: Action[AnyContent] = Action.async { implicit request =>
    val jsonRequest = request.body.asJson
    localWithJsonBody({ emailInformation =>
      emailConnector.sendEmail(emailInformation)
    }, jsonRequest.getOrElse(JsNull))
  }

  private def localWithJsonBody(f: EmailInformation => Future[Result], request: JsValue): Future[Result] =
    Try(request.validate[EmailInformation]) match {
      case Success(JsSuccess(payload, _)) => f(payload)
      case Success(JsError(errs))         => Future successful BadRequest(s"Invalid payload: $errs")
      case Failure(e)                     => Future successful BadRequest(s"could not parse body due to ${e.getMessage}")
    }

}
