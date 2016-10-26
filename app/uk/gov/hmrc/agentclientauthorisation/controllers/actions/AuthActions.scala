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
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier

import scala.concurrent.Future

trait AuthActions {

  def authConnector: AuthConnector
  def agenciesFakeConnector: AgenciesFakeConnector

  private val withAccounts = new ActionBuilder[RequestWithAccounts] with ActionRefiner[Request, RequestWithAccounts] {
    protected def refine[A](request: Request[A]): Future[Either[Result, RequestWithAccounts[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      authConnector.currentAccounts()
        .map(accounts => new RequestWithAccounts(accounts, request))
        .map(Right(_))
        .recover({
          case e: uk.gov.hmrc.play.http.Upstream4xxResponse if e.upstreamResponseCode == 401 =>
            Left(Results.Unauthorized)
        })
    }
  }

  val onlyForSaAgents = withAccounts andThen new ActionRefiner[RequestWithAccounts, AgentRequest] {
    override protected def refine[A](request: RequestWithAccounts[A]): Future[Either[Result, AgentRequest[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      request.accounts.agent match {
        case Some(r) => agenciesFakeConnector.findArn(r).map {
          case Some(arn) => Right(AgentRequest(arn, request))
          case None => Left(Results.Unauthorized)
        }
        case None => Future successful Left(Results.Unauthorized)
      }
    }
  }



  val onlyForSaClients = withAccounts andThen new ActionRefiner[RequestWithAccounts, SaClientRequest] {
    override protected def refine[A](request: RequestWithAccounts[A]): Future[Either[Result, SaClientRequest[A]]] = {
      implicit val hc = HeaderCarrier.fromHeadersAndSession(request.headers, None)
      request.accounts.sa match {
        case Some(saUtr) => agenciesFakeConnector.findClient(saUtr) map {
          case Some(mtdClientId) => Right(SaClientRequest(saUtr, mtdClientId, request))
          case None => Left(Results.Unauthorized)
        }
        case _ => Future successful Left(Results.Unauthorized)
      }
    }
  }


}

class RequestWithAccounts[A](val accounts: Accounts, request: Request[A]) extends WrappedRequest[A](request)
case class AgentRequest[A](arn: Arn, request: Request[A]) extends WrappedRequest[A](request)
case class SaClientRequest[A](saUtr: SaUtr, mtdClientId: MtdClientId, request: Request[A]) extends WrappedRequest[A](request)
