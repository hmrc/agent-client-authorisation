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

package uk.gov.hmrc.agentclientauthorisation.connectors

import java.net.URL

import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics
import javax.inject._
import play.api.Logger
import play.api.mvc._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.auth.core
import uk.gov.hmrc.auth.core.AffinityGroup.{Individual, Organisation}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.{affinityGroup, allEnrolments, confidenceLevel, credentials}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval, ~}
import uk.gov.hmrc.domain.TaxIdentifier
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.HeaderCarrierConverter
import uk.gov.hmrc.play.bootstrap.controller.BaseController

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

case class Authority(mtdItId: Option[MtdItId], enrolmentsUrl: URL)

case class AgentRequest[A](arn: Arn, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class AuthActions @Inject()(metrics: Metrics, val authConnector: AuthConnector)
    extends HttpAPIMonitor with AuthorisedFunctions with BaseController {
  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private type AgentAuthAction = Request[AnyContent] => Arn => Future[Result]

  private val affinityGroupAllEnrolls
    : Retrieval[~[Option[AffinityGroup], core.Enrolments]] = affinityGroup and allEnrolments
  private val affinityGroupConfidenceAllEnrols
    : Retrieval[Option[AffinityGroup] ~ ConfidenceLevel ~ Enrolments] = affinityGroup and confidenceLevel and allEnrolments
  private val AuthProvider: AuthProviders = AuthProviders(GovernmentGateway)
  private val agentEnrol = "HMRC-AS-AGENT"
  private val agentEnrolId = "AgentReferenceNumber"
  private val isAnAgent = true

  def onlyForAgents(action: AgentAuthAction)(implicit ec: ExecutionContext): Action[AnyContent] = Action.async {
    implicit request ⇒
      authorised(AuthProvider).retrieve(affinityGroupAllEnrolls) {
        case Some(affinityG) ~ allEnrols ⇒
          (isAgent(affinityG), extractEnrolmentData(allEnrols.enrolments, agentEnrol, agentEnrolId)) match {
            case (`isAnAgent`, Some(arn)) => action(request)(Arn(arn))
            case (_, None)                => Future successful AgentNotSubscribed
            case _                        => Future successful GenericUnauthorized
          }
        case _ => Future successful GenericUnauthorized
      } recover {
        case e: AuthorisationException =>
          Logger.error("Failed to auth", e)
          GenericUnauthorized
      }
  }

  private def isAgent(group: AffinityGroup): Boolean = group.toString.contains("Agent")

  def onlyForClients[T <: TaxIdentifier](service: Service, clientIdType: ClientIdType[T])(
    action: Request[AnyContent] => ClientIdentifier[T] => Future[Result])(
    implicit ec: ExecutionContext): Action[AnyContent] = Action.async { implicit request =>
    authorised(AuthProvider).retrieve(allEnrolments) { allEnrols =>
      val clientId = extractEnrolmentData(allEnrols.enrolments, service.enrolmentKey, clientIdType.enrolmentId)
      if (clientId.isDefined) action(request)(ClientIdentifier(clientIdType.createUnderlying(clientId.get)))
      else Future successful ClientNinoNotFound
    } recover {
      case e: AuthorisationException =>
        Logger.error("Failed to auth", e)
        GenericUnauthorized
    }
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
            enrolment.key,
            enrolment.identifiers.map(identifier =>
              EnrolmentIdentifier(identifier.key, identifier.value.replace(" ", ""))),
            enrolment.state,
            enrolment.delegatedAuthRule
        ))

    TypeOfEnrolment(clientId)
      .extractIdentifierFrom(trimmedEnrolments)
      .contains(clientId)
  }

  type BadRequest = Result

  def determineService(service: String, identifier: String)(
    implicit hc: HeaderCarrier): Either[BadRequest, (Service, TaxIdentifier)] =
    service match {
      case "MTDITID" if MtdItIdType.isValid(identifier) =>
        Right(Service.MtdIt, MtdItIdType.createUnderlying(identifier))
      case "NI" if NinoType.isValid(identifier) =>
        Right(Service.PersonalIncomeRecord, NinoType.createUnderlying(identifier))
      case "VRN" if VrnType.isValid(identifier) => Right(Service.Vat, VrnType.createUnderlying(identifier))
      case "UTR" if UtrType.isValid(identifier) => Right(Service.Trust, UtrType.createUnderlying(identifier))
      case e                                    => Left(BadRequest(s"Unsupported $e"))
    }

  def AuthorisedClientOrStrideUser[T](service: String, identifier: String, strideRoles: Seq[String])(
    body: RequestAndCurrentUser)(implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromHeadersAndSession(request.headers, None)
      authorised().retrieve(allEnrolments and credentials) {
        case enrolments ~ creds =>
          determineService(service, identifier) match {
            case Right((clientService, clientId)) =>
              creds.providerType match {
                case "GovernmentGateway" if hasRequiredEnrolmentMatchingIdentifier(enrolments, clientId) =>
                  body(request)(CurrentUser(enrolments, creds, clientService, clientId))
                case "PrivilegedApplication" if hasRequiredStrideRole(enrolments, strideRoles) =>
                  body(request)(CurrentUser(enrolments, creds, clientService, clientId))
                case _ =>
                  Future successful NoPermissionToPerformOperation
              }
            case Left(error) => Future successful error
          }
      } recover {
        case e: AuthorisationException =>
          Logger.error("Failed to auth", e)
          GenericUnauthorized
      }
    }

  def withClientIdentifiedBy(action: Seq[(Service, String)] => Future[Result])(
    implicit request: Request[AnyContent],
    ec: ExecutionContext): Future[Result] =
    authorised(AuthProvider and (Individual or Organisation))
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
      }

  private def extractAffinityGroup(affinityGroup: AffinityGroup): String =
    (affinityGroup.toJson \ "affinityGroup").as[String]

  protected def withMultiEnrolledClient[A](
    body: Seq[(String, String)] => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(AuthProviders(GovernmentGateway) and (AffinityGroup.Individual or AffinityGroup.Organisation))
      .retrieve(affinityGroupConfidenceAllEnrols) {
        case Some(affinity) ~ confidence ~ enrols =>
          val clientIdTypePlusIds: Seq[(String, String)] = enrols.enrolments.map { enrolment =>
            (enrolment.identifiers.head.key, enrolment.identifiers.head.value.replaceAll(" ", ""))
          }.toSeq
          (affinity, confidence) match {
            case (AffinityGroup.Individual, cl) if cl >= ConfidenceLevel.L200 => body(clientIdTypePlusIds)
            case (AffinityGroup.Organisation, _)                              => body(clientIdTypePlusIds)
            case _                                                            => Future successful GenericUnauthorized
          }
        case _ => Future successful GenericUnauthorized
      }

  def extractEnrolmentData(enrolls: Set[Enrolment], enrolKey: String, enrolId: String): Option[String] =
    enrolls.find(_.key == enrolKey).flatMap(_.getIdentifier(enrolId)).map(_.value)
}
