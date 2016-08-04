/*
 * Copyright 2016 HM Revenue & Customs
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
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AuthConnector, Enrolments, UserInfo}
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AuthActions {

  def authConnector: AuthConnector

  private val withUserInfo = new ActionBuilder[RequestWithUserInfo] with ActionRefiner[Request, RequestWithUserInfo] {
    protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithUserInfo[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      authConnector.currentUserInfo()
        .map(ui => new RequestWithUserInfo(ui, request))
        .map(Right(_))
        .recover({
          case e: uk.gov.hmrc.play.http.Upstream4xxResponse if e.upstreamResponseCode == 401 =>
            Left(Results.Unauthorized)
        })
    }
  }

  val onlyForSaAgents = withUserInfo andThen new ActionRefiner[RequestWithUserInfo, AgentRequest] {
    override protected def refine[A](request: RequestWithUserInfo[A]): Future[Either[Result, AgentRequest[A]]] =
      Future successful ((request.userInfo.accounts.agent, request.userInfo.hasActivatedIrSaAgentEnrolment) match {
        case (Some(agentCode), true) => Right(new AgentRequest(agentCode,request.userInfo.userDetailsLink, request))
        case _ => Left(Results.Unauthorized)
      })
  }

  val onlyForSaClients = withUserInfo andThen new ActionRefiner[RequestWithUserInfo, SaClientRequest] {
    override protected def refine[A](request: RequestWithUserInfo[A]): Future[Either[Result, SaClientRequest[A]]] = {
      Future successful (request.userInfo.accounts.sa match {
        case Some(saUtr) => Right(new SaClientRequest(saUtr, request))
        case _ => Left(Results.Unauthorized)
      })
    }
  }

  val saClientsOrAgents = withUserInfo andThen new ActionRefiner[RequestWithUserInfo, Request] {
    override protected def refine[A](request: RequestWithUserInfo[A]): Future[Either[Result, Request[A]]] = {
      Future successful (request.userInfo match {
        case UserInfo(Accounts(Some(agentCode), _), _, true) => Right(new AgentRequest(agentCode,  request.userInfo.userDetailsLink, request))
        case UserInfo(Accounts(None, Some(saUtr)), _, _) => Right(new SaClientRequest(saUtr, request))
        case _ => Left(Results.Unauthorized)
      })
    }
  }

}

class RequestWithUserInfo[A](val userInfo: UserInfo, request: Request[A]) extends WrappedRequest[A](request)
case class AgentRequest[A](agentCode: AgentCode, userDetailsLink: String, request: Request[A]) extends WrappedRequest[A](request)
case class SaClientRequest[A](saUtr: SaUtr, request: Request[A]) extends WrappedRequest[A](request)
