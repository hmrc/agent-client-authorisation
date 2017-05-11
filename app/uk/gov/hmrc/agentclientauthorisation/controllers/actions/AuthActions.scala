/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.controllers.actions

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.mvc._
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthConnector, Authority}
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AuthActions {

  def authConnector: AuthConnector

  protected val withAuthority = new ActionBuilder[RequestWithAuthority] with ActionRefiner[Request, RequestWithAuthority] {
    protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithAuthority[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      authConnector.currentAuthority()
        .map(authority => new RequestWithAuthority(authority, request))
        .map(Right(_))
        .recover({
          case e: uk.gov.hmrc.play.http.Upstream4xxResponse if e.upstreamResponseCode == 401 =>
            Left(GenericUnauthorized)
        })
    }
  }

  protected val onlyForAgents = new ActionBuilder[AgentRequest] with ActionRefiner[Request, AgentRequest] {
    protected def refine[A](request: Request[A]): Future[Either[Result, AgentRequest[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      authConnector.currentArn()
        .map {
          case Some(arn) => Right(AgentRequest(arn, request))
          case None => Left(AgentRegistrationNotFound)
        }
        .recover({
          case e: uk.gov.hmrc.play.http.Upstream4xxResponse if e.upstreamResponseCode == 401 =>
            Left(GenericUnauthorized)
        })
    }
  }


  val onlyForClients: ActionBuilder[ClientRequest] = withAuthority andThen new ActionRefiner[RequestWithAuthority, ClientRequest] {
    override protected def refine[A](request: RequestWithAuthority[A]): Future[Either[Result, ClientRequest[A]]] = {
      request.authority.nino match {
        case Some(nino) => Future successful Right(ClientRequest(nino, request))
        case None => Future successful Left(ClientEnrolmentNotFound)
      }
    }
  }
}

class RequestWithAuthority[A](val authority: Authority, request: Request[A]) extends WrappedRequest[A](request)

case class AgentRequest[A](arn: Arn, request: Request[A]) extends WrappedRequest[A](request)

case class ClientRequest[A](nino: Nino, request: Request[A]) extends WrappedRequest[A](request)
