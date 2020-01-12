/*
 * Copyright 2020 HM Revenue & Customs
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

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject._
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AffinityGroup.{Individual, Organisation}
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, allEnrolments, confidenceLevel, credentials}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class Authority(mtdItId: Option[MtdItId], enrolmentsUrl: URL)

case class AgentRequest[A](arn: Arn, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class AuthActions @Inject()(metrics: Metrics, val authConnector: AuthConnector, cc: ControllerComponents)
    extends BackendController(cc) with HttpAPIMonitor with AuthorisedFunctions {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private type AgentAuthAction = Request[AnyContent] => Arn => Future[Result]

  private val authProvider: AuthProviders = AuthProviders(GovernmentGateway)

  private val agentEnrol = "HMRC-AS-AGENT"
  private val agentEnrolId = "AgentReferenceNumber"
  private val isAnAgent = true

  def onlyForAgents(action: AgentAuthAction)(implicit ec: ExecutionContext): Action[AnyContent] = Action.async {
    implicit request ⇒
      authorised(authProvider).retrieve(affinityGroup and allEnrolments) {
        case Some(affinityG) ~ allEnrols ⇒
          (isAgent(affinityG), extractEnrolmentData(allEnrols.enrolments, agentEnrol, agentEnrolId)) match {
            case (`isAnAgent`, Some(arn)) => action(request)(Arn(arn))
            case (_, None)                => Future successful AgentNotSubscribed
            case _                        => Future successful GenericUnauthorized
          }
        case _ => Future successful GenericUnauthorized
      } recover handleFailure
  }

  def withBasicAuth[A](body: => Future[Result])(
    implicit
    request: Request[A],
    hc: HeaderCarrier,
    ec: ExecutionContext): Future[Result] = authorised() { body }

  private def isAgent(group: AffinityGroup): Boolean = group.toString.contains("Agent")

  def onlyForClients[T <: TaxIdentifier](service: Service, clientIdType: ClientIdType[T])(
    action: Request[AnyContent] => ClientIdentifier[T] => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] = Action.async { implicit request =>
    authorised(authProvider).retrieve(allEnrolments) { allEnrols =>
      val clientId = extractEnrolmentData(allEnrols.enrolments, service.enrolmentKey, clientIdType.enrolmentId)
      if (clientId.isDefined) action(request)(ClientIdentifier(clientIdType.createUnderlying(clientId.get)))
      else Future successful NotAClient
    } recover handleFailure
  }

  protected type RequestAndCurrentUser = Request[AnyContent] => CurrentUser => Future[Result]

  case class CurrentUser(
    enrolments: Enrolments,
    credentials: Credentials,
    service: Service,
    taxIdentifier: TaxIdentifier)

  def hasRequiredStrideRole(enrolments: Enrolments, strideRoles: Seq[String]): Boolean =
    strideRoles.exists(s => enrolments.enrolments.exists(_.key == s))

  def hasRequiredEnrolmentMatchingIdentifier(enrolments: Enrolments, clientId: TaxIdentifier): Boolean = {
    val trimmedEnrolments: Set[Enrolment] = enrolments.enrolments
      .map(
        enrolment =>
          Enrolment(
            enrolment.key.trim,
            enrolment.identifiers.map(identifier =>
              EnrolmentIdentifier(identifier.key, identifier.value.replace(" ", ""))),
            enrolment.state,
            enrolment.delegatedAuthRule
        ))

    TypeOfEnrolment(clientId)
      .extractIdentifierFrom(trimmedEnrolments)
      .contains(clientId)
  }

  def validateClientId(clientIdType: String, clientId: String): Either[Result, (Service, TaxIdentifier)] =
    clientIdType match {
      case "MTDITID" if MtdItIdType.isValid(clientId) =>
        Right((MtdIt, MtdItId(clientId)))
      case "NI" if NinoType.isValid(clientId) =>
        Right((PersonalIncomeRecord, Nino(clientId)))
      case "VRN" if VrnType.isValid(clientId)         => Right((Vat, Vrn(clientId)))
      case "UTR" if UtrType.isValid(clientId)         => Right((Trust, Utr(clientId)))
      case "CGTPDRef" if CgtRefType.isValid(clientId) => Right((CapitalGains, CgtRef(clientId)))
      case e =>
        Left(BadRequest(s"Unsupported $e or Invalid ClientId"))
    }

  def AuthorisedClientOrStrideUser[T](service: String, identifier: String, strideRoles: Seq[String])(
    body: RequestAndCurrentUser)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      authorised().retrieve(allEnrolments and credentials) {
        case enrolments ~ Some(creds) =>
          validateClientId(service, identifier) match {
            case Right((clientService, clientId)) =>
              creds.providerType match {
                case "GovernmentGateway" if hasRequiredEnrolmentMatchingIdentifier(enrolments, clientId) =>
                  body(request)(CurrentUser(enrolments, creds, clientService, clientId))
                case "PrivilegedApplication" if hasRequiredStrideRole(enrolments, strideRoles) =>
                  body(request)(CurrentUser(enrolments, creds, clientService, clientId))
                case e =>
                  Logger(getClass).warn(
                    s"ProviderType found: $e or hasRequiredEnrolmentMatchingIdentifier: ${hasRequiredEnrolmentMatchingIdentifier(enrolments, clientId)}")
                  Future successful GenericForbidden
              }
            case Left(error) => Future successful error
          }
        case e =>
          Logger(getClass).warn(s"No Creds Found: $e")
          Future successful GenericForbidden
      } recover handleFailure
    }

  def onlyStride(strideRole: String)(body: Request[AnyContent] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      authorised(AuthProviders(PrivilegedApplication)).retrieve(allEnrolments) {
        case allEnrols if allEnrols.enrolments.map(_.key).contains(strideRole) =>
        body(request)
        case e =>
          Logger(getClass).warn(s"Unauthorized Discovered during Stride Authentication: ${e.enrolments.map(_.key)}")
          Future successful Unauthorized
      }.recover {
        case e =>
          Logger(getClass).warn(s"Error Discovered during Stride Authentication: ${e.getMessage}")
          GenericForbidden
      }
    }

  def withClientIdentifiedBy(action: Seq[(Service, String)] => Future[Result])(
    agentResponse: Result)(implicit request: Request[AnyContent], ec: ExecutionContext): Future[Result] =
    authorised(authProvider and (Individual or Organisation))
      .retrieve(allEnrolments) { allEnrols =>
        val identifiers: Seq[(Service, String)] = Service.supportedServices
          .map { service =>
            allEnrols.enrolments
              .find(_.key == service.enrolmentKey)
              .flatMap(_.identifiers.headOption)
              .map(i => (service, i.value))
          }
          .collect {
            case Some(x) => x
          }
        action(identifiers)
      } recoverWith {
      case _: UnsupportedAffinityGroup => Future.successful(agentResponse) //return default status for agents
      case p                           => Future.successful(handleFailure(request)(p))
    }

  protected def withMultiEnrolledClient[A](body: Seq[(String, String)] => Future[Result])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext,
    request: Request[_]): Future[Result] =
    authorised(authProvider and (Individual or Organisation))
      .retrieve(affinityGroup and confidenceLevel and allEnrolments) {
        case Some(affinity) ~ confidence ~ enrols =>
          val clientIdTypePlusIds: Seq[(String, String)] = enrols.enrolments.map { enrolment =>
            (enrolment.identifiers.head.key, enrolment.identifiers.head.value.replaceAll(" ", ""))
          }.toSeq

          (affinity, confidence) match {
            case (Individual, cl) if cl >= L200 => body(clientIdTypePlusIds)
            case (Organisation, _)              => body(clientIdTypePlusIds)
            case _                              => Future successful GenericForbidden
          }
        case _ => Future successful GenericUnauthorized
      } recover handleFailure

  def extractEnrolmentData(enrolls: Set[Enrolment], enrolKey: String, enrolId: String): Option[String] =
    enrolls.find(_.key == enrolKey).flatMap(_.getIdentifier(enrolId)).map(_.value)

  def handleFailure(implicit request: Request[_]): PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      GenericUnauthorized

    case _: InsufficientEnrolments ⇒
      Logger.warn(s"Logged in user does not have required enrolments")
      GenericForbidden

    case _: UnsupportedAuthProvider ⇒
      Logger.warn(s"user logged in with unsupported auth provider")
      GenericForbidden

    case _: UnsupportedAffinityGroup ⇒
      Logger.warn(s"user logged in with unsupported AffinityGroup")
      GenericForbidden
  }
}
