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

package uk.gov.hmrc.agentclientauthorisation.support

import org.mockito.Matchers.{any, eq => eqs}
import org.mockito.Mockito._
import uk.gov.hmrc.agentclientauthorisation.connectors.{Accounts, AgenciesFakeConnector, AuthConnector}
import uk.gov.hmrc.agentclientauthorisation.model.Arn
import uk.gov.hmrc.domain.{AgentCode, Generator, Nino}
import uk.gov.hmrc.play.http.Upstream4xxResponse

import scala.concurrent.Future

trait AuthMocking {

  def authConnector: AuthConnector
  def agenciesFakeConnector : AgenciesFakeConnector
  def generator: Generator

  private val defaultArn = Arn("12345")

  def givenAgentIsLoggedIn(arn : Arn = defaultArn): Arn = {
    givenAccountsAre(Accounts(Some(AgentCode("54321")), None))
    givenAgencyRecordIs(AgentCode("54321"), arn)
    arn
  }

  def givenAgentWithoutRecordIsLoggedIn() = {
    givenAccountsAre(Accounts(Some(AgentCode("54321")), None))
    givenUserHasNoAgency(AgentCode("54321"))
  }

  def givenClientIsLoggedIn() = {
    givenAccountsAre(Accounts(None, Some(generator.nextNino)))
    val nino = generator.nextNino
  }

  def givenNonMTDClientIsLoggedIn() = {
    givenAccountsAre(Accounts(None, Some(generator.nextNino)))
  }

  def givenClientIsLoggedInWithNoSAAccount() = {
    givenAccountsAre(Accounts(None, None))
  }

  def givenUserIsNotLoggedIn() = whenAccountsIsAskedFor().thenReturn(Future failed Upstream4xxResponse("msg", 401, 401))

  def givenAccountsAre(accounts: Accounts) = whenAccountsIsAskedFor().thenReturn(Future successful accounts)

  def whenAccountsIsAskedFor() = when(authConnector.currentAccounts()(any(), any()))

  def givenAgencyRecordIs(agentCode: AgentCode, arn: Arn) = when(agenciesFakeConnector.findArn(eqs(agentCode))(any(), any())).thenReturn(Future successful Some(arn))

  def givenUserHasNoAgency(agentCode: AgentCode) = when(agenciesFakeConnector.findArn(eqs(agentCode))(any(), any())).thenReturn(Future successful None)


}
