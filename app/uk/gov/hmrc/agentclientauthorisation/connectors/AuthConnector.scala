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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL
import javax.inject._

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.Service
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.{Retrieval, ~}
import uk.gov.hmrc.auth.core.retrieve.Retrievals.{affinityGroup, allEnrolments}
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future
import scala.language.postfixOps

case class Authority(mtdItId: Option[MtdItId],
                     enrolmentsUrl: URL)

case class AgentRequest[A](arn: Arn, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class AuthConnector @Inject()(metrics: Metrics,
                              microserviceAuthConnector: PlayAuthConnector)
  extends HttpAPIMonitor with AuthorisedFunctions with BaseController {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  override def authConnector: core.AuthConnector = microserviceAuthConnector

  private type AgentAuthAction = Request[AnyContent] => Arn => Future[Result]

  private val affinityGroupAllEnrolls: Retrieval[~[Option[AffinityGroup], core.Enrolments]] = affinityGroup and allEnrolments
  private val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)
  private val agentEnrol = "HMRC-AS-AGENT"
  private val agentEnrolId = "AgentReferenceNumber"
  private val isAnAgent = true

  def onlyForAgents(action: AgentAuthAction): Action[AnyContent] = Action.async {
    implicit request ⇒
      authorised(AuthProvider).retrieve(affinityGroupAllEnrolls) {
        case Some(affinityG) ~ allEnrols ⇒
          (isAgent(affinityG), extractEnrolmentData(allEnrols.enrolments, agentEnrol, agentEnrolId)) match {
            case (`isAnAgent`, Some(arn)) => action(request)(Arn(arn))
            case (_, None) => Future successful AgentNotSubscribed
            case _ => Future successful GenericUnauthorized
          }
        case _ => Future successful GenericUnauthorized
      } recover {
        case e @ _ => {
          Logger.error("Failed to auth", e)
          GenericUnauthorized
        }
      }
  }

  private def isAgent(group: AffinityGroup): Boolean = group.toString.contains("Agent")

  def onlyForClients[T<:TaxIdentifier](service: Service, clientIdType: String)
                    (action: Request[AnyContent] => T => Future[Result])
                    (taxId: (String) => T): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(AuthProvider).retrieve(allEnrolments) {
        allEnrols =>
          val clientId = extractEnrolmentData(allEnrols.enrolments, service.enrolmentKey, enrolmentId(clientIdType))
          if (clientId.isDefined) action(request)(taxId(clientId.get))
          else Future successful ClientNinoNotFound
      } recover {
        case _ => GenericUnauthorized
      }
  }

  // TODO remove this nasty translation when we create a ClientId type
  def enrolmentId(clientIdType: String): String = clientIdType match {
    case CLIENT_ID_TYPE_NINO  => "NINO"
    case CLIENT_ID_TYPE_MTDITID => CLIENT_ID_TYPE_MTDITID
  }

  private def extractEnrolmentData(enrolls: Set[Enrolment], enrolKey: String, enrolId: String): Option[String] =
    enrolls.find(_.key == enrolKey).flatMap(_.getIdentifier(enrolId)).map(_.value)
}
