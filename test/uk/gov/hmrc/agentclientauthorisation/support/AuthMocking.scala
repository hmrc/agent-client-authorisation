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

import java.net.URL

import org.mockito.Matchers.{any, eq => eqs}
import org.mockito.Mockito._
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthConnector, Authority}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.domain.Generator
import uk.gov.hmrc.play.http.Upstream4xxResponse

import scala.concurrent.Future

trait AuthMocking {
  private val enrolmentsNotNeededForThisTest = new URL("http://localhost/enrolments-not-specified")

  def authConnector: AuthConnector
  def generator: Generator

  private val defaultArn = Arn("12345")

  def givenAgentIsLoggedIn(arn: Arn = defaultArn) = {
    givenAgencyArnIs(arn)
    givenNoAccounts()
  }

  def givenAgentWithoutRecordIsLoggedIn() = {
    givenAccountsAre(Authority(None, enrolmentsUrl = enrolmentsNotNeededForThisTest))
    givenUserHasNoAgency()
  }

  def givenClientIsLoggedIn() = {
    givenAccountsAre(Authority(Some(generator.nextNino), enrolmentsUrl = enrolmentsNotNeededForThisTest))
    givenUserHasNoAgency()
    val nino = generator.nextNino
  }

  def givenNonMTDClientIsLoggedIn() = {
    givenAccountsAre(Authority(Some(generator.nextNino), enrolmentsUrl = enrolmentsNotNeededForThisTest))
    givenUserHasNoAgency()
  }

  def givenClientIsLoggedInWithNoSAAccount() = {
    givenAccountsAre(Authority(None, enrolmentsUrl = enrolmentsNotNeededForThisTest))
    givenUserHasNoAgency()
  }

  def givenUserIsNotLoggedIn() = {
    whenAccountsIsAskedFor().thenReturn(Future failed Upstream4xxResponse("msg", 401, 401))
    when(authConnector.currentArn()(any(), any())).thenReturn(Future failed Upstream4xxResponse("msg", 401, 401))
  }

  def givenAccountsAre(authority: Authority) = whenAccountsIsAskedFor().thenReturn(Future successful authority)

  def givenNoAccounts() = whenAccountsIsAskedFor().thenReturn(Future failed new Exception)

  def whenAccountsIsAskedFor() = when(authConnector.currentAuthority()(any(), any()))

  def givenAgencyArnIs(arn: Arn) = when(authConnector.currentArn()(any(), any())).thenReturn(Future successful Some(arn))

  def givenUserHasNoAgency() = when(authConnector.currentArn()(any(), any())).thenReturn(Future successful None)


}
