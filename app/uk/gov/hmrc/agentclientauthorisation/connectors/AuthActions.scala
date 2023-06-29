/*
 * Copyright 2023 HM Revenue & Customs
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
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64
import com.codahale.metrics.MetricRegistry
import com.kenshoo.play.metrics.Metrics

import javax.inject._
import play.api.{Logger, Logging}
import play.api.mvc._
import uk.gov.hmrc.agent.kenshoo.monitoring.HttpAPIMonitor
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, CbcId, CgtRef, CgtRefType, ClientIdType, ClientIdentifier, MtdItId, MtdItIdType, NinoType, PptRef, Service, Urn, UrnType, Utr, UtrType, Vrn, VrnType}
import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.auth.core.AffinityGroup.{Individual, Organisation}
import uk.gov.hmrc.auth.core.AuthProvider.{GovernmentGateway, PrivilegedApplication}
import uk.gov.hmrc.auth.core.ConfidenceLevel.L200
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.{affinityGroup, allEnrolments, confidenceLevel, credentials}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.{HeaderCarrier, HeaderNames}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.play.http.HeaderCarrierConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.matching.Regex

case class Authority(mtdItId: Option[MtdItId], enrolmentsUrl: URL)

case class AgentRequest[A](arn: Arn, request: Request[A]) extends WrappedRequest[A](request)

@Singleton
class AuthActions @Inject()(metrics: Metrics, appConfig: AppConfig, val authConnector: AuthConnector, cc: ControllerComponents)
    extends BackendController(cc) with HttpAPIMonitor with AuthorisedFunctions with Logging {

  override val kenshooRegistry: MetricRegistry = metrics.defaultRegistry

  private type AgentAuthAction = Request[AnyContent] => Arn => Future[Result]

  private val authProvider: AuthProviders = AuthProviders(GovernmentGateway)

  private val agentEnrol = "HMRC-AS-AGENT"
  private val agentEnrolId = "AgentReferenceNumber"
  private val isAnAgent = true

  def onlyForAgents(action: AgentAuthAction)(implicit ec: ExecutionContext): Action[AnyContent] = Action.async { implicit request ⇒
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

  val basicAuthHeader: Regex = "Basic (.+)".r
  val decodedAuth: Regex = "(.+):(.+)".r

  private def decodeFromBase64(encodedString: String): String =
    try {
      new String(Base64.getDecoder.decode(encodedString), UTF_8)
    } catch { case _: Throwable => "" }

  def withBasicAuth(expectedAuth: BasicAuthentication)(body: => Future[Result])(implicit request: Request[_]): Future[Result] =
    request.headers.get(HeaderNames.authorisation) match {
      case Some(basicAuthHeader(encodedAuthHeader)) =>
        decodeFromBase64(encodedAuthHeader) match {
          case decodedAuth(username, password) =>
            if (BasicAuthentication(username, password) == expectedAuth) body
            else {
              logger.warn("Authorization header found in the request but invalid username or password")
              Future successful Unauthorized
            }
          case _ =>
            logger.warn("Authorization header found in the request but its not in the expected format")
            Future successful Unauthorized
        }
      case _ =>
        logger.warn("No Authorization header found in the request for agent termination")
        Future successful Unauthorized
    }

  def withBasicAuth[A](body: => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised() { body }

  private def isAgent(group: AffinityGroup): Boolean = group.toString.contains("Agent")

  def onlyForClients[T <: TaxIdentifier](service: Service, clientIdType: ClientIdType[T])(
    action: Request[AnyContent] => ClientIdentifier[T] => Future[Result])(implicit ec: ExecutionContext): Action[AnyContent] = Action.async {
    implicit request =>
      authorised(authProvider).retrieve(allEnrolments) { allEnrols =>
        val clientId = extractEnrolmentData(allEnrols.enrolments, service.enrolmentKey, clientIdType.enrolmentId)
        if (clientId.isDefined) action(request)(ClientIdentifier(clientIdType.createUnderlying(clientId.get)))
        else Future successful NotAClient
      } recover handleFailure
  }

  protected type RequestAndCurrentUser = Request[AnyContent] => CurrentUser => Future[Result]

  case class CurrentUser(enrolments: Enrolments, credentials: Credentials, service: Service, taxIdentifier: TaxIdentifier)

  def hasRequiredStrideRole(enrolments: Enrolments, strideRoles: Seq[String]): Boolean =
    strideRoles.exists(s => enrolments.enrolments.exists(_.key == s))

  def hasRequiredEnrolmentMatchingIdentifier(enrolments: Enrolments, clientId: TaxIdentifier): Boolean = {
    val trimmedEnrolments: Set[Enrolment] = enrolments.enrolments
      .map(
        enrolment =>
          Enrolment(
            enrolment.key.trim,
            enrolment.identifiers.map(identifier => EnrolmentIdentifier(identifier.key, identifier.value.replace(" ", ""))),
            enrolment.state,
            enrolment.delegatedAuthRule
        ))

    // check that among the identifiers that the user has, there is one that matches the clientId provided
    clientId match {
      // need to handle Arn separately as it is not one of our managed services
      case Arn(arn) =>
        trimmedEnrolments.exists(enrolment =>
          enrolment.key == "HMRC-AS-AGENT" && enrolment.identifiers.contains(EnrolmentIdentifier("AgentReferenceNumber", arn)))
      case taxId: TaxIdentifier =>
        val requiredTaxIdType = ClientIdentifier(taxId).enrolmentId
        trimmedEnrolments
          .flatMap(_.identifiers)
          .filter(_.key == requiredTaxIdType)
          .exists(_.value == clientId.value)
    }
  }

  def validateClientId(clientIdType: String, clientId: String): Either[Result, (Service, TaxIdentifier)] =
    clientIdType match {
      case "MTDITID" if appConfig.altItsaEnabled & NinoType.isValid(clientId) => Right((MtdIt, Nino(clientId)))
      case "MTDITID" if MtdItIdType.isValid(clientId) =>
        Right((MtdIt, MtdItId(clientId)))
      case "NI" if NinoType.isValid(clientId) =>
        Right((PersonalIncomeRecord, Nino(clientId)))
      case "VRN" if VrnType.isValid(clientId)                   => Right((Vat, Vrn(clientId)))
      case "UTR" if UtrType.isValid(clientId)                   => Right((Trust, Utr(clientId)))
      case "URN" if UrnType.isValid(clientId)                   => Right((TrustNT, Urn(clientId)))
      case "CGTPDRef" if CgtRefType.isValid(clientId)           => Right((CapitalGains, CgtRef(clientId)))
      case "EtmpRegistrationNumber" if PptRef.isValid(clientId) => Right((Ppt, PptRef(clientId)))
      case "cbcId" if CbcId.isValid(clientId)                   => Right((Cbc, CbcId(clientId)))
      case e                                                    => Left(BadRequest(s"Unsupported $e or Invalid ClientId"))
    }

  def AuthorisedClientOrStrideUser[T](clientIdType: String, identifier: String, strideRoles: Seq[String])(body: RequestAndCurrentUser)(
    implicit ec: ExecutionContext): Action[AnyContent] =
    Action.async { implicit request =>
      implicit val hc: HeaderCarrier = HeaderCarrierConverter.fromRequest(request)
      authorised().retrieve(allEnrolments and credentials) {
        case enrolments ~ Some(creds) =>
          validateClientId(clientIdType, identifier) match {
            case Right((clientService, clientId)) =>
              creds.providerType match {
                case "GovernmentGateway" if hasRequiredEnrolmentMatchingIdentifier(enrolments, clientId) =>
                  body(request)(CurrentUser(enrolments, creds, clientService, clientId))
                case "PrivilegedApplication" if hasRequiredStrideRole(enrolments, strideRoles) =>
                  body(request)(CurrentUser(enrolments, creds, clientService, clientId))
                case e =>
                  logger.warn(
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
      authorised(AuthProviders(PrivilegedApplication))
        .retrieve(allEnrolments) {
          case allEnrols if allEnrols.enrolments.map(_.key).contains(strideRole) =>
            body(request)
          case e =>
            logger.warn(s"Unauthorized Discovered during Stride Authentication: ${e.enrolments.map(_.key)}")
            Future successful Unauthorized
        }
        .recover {
          case e =>
            logger.warn(s"Error Discovered during Stride Authentication: ${e.getMessage}")
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
      case p                           => Future.successful(handleFailure(p))
    }

  private def supportedServiceName(key: String): Option[String] =
    Service.supportedServices.find(_.enrolmentKey == key).map(_.id)

  private def maybeNinoForMtdItId(enrols: Enrolments) =
    if (appConfig.altItsaEnabled)
      enrols.enrolments
        .find(_.key == "HMRC-NI")
        .map(ninoEnrol => ("HMRC-MTD-IT", ninoEnrol.identifiers.head.key, ninoEnrol.identifiers.head.value.replaceAll(" ", "")))
    else None

  protected def withMultiEnrolledClient[A](
    body: Seq[(String, String, String)] => Future[Result])(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Result] =
    authorised(authProvider and (Individual or Organisation))
      .retrieve(affinityGroup and confidenceLevel and allEnrolments) {
        case Some(affinity) ~ confidence ~ enrols =>
          val clientIdTypePlusIds: Seq[(String, String, String)] =
            enrols.enrolments
              .filter(enrol => Service.supportedServices.map(_.enrolmentKey).contains(enrol.key))
              .map { supportedEnrol =>
                if (supportedEnrol.key == Service.HMRCCBCORG) {
                  ( // TODO - handle better, multi-identifier lookup? Uk cbc has UTR first
                    supportedServiceName(supportedEnrol.key).getOrElse(
                      throw new RuntimeException(s"service name not found for supported enrolment $supportedEnrol")),
                    supportedEnrol.identifiers.reverse.head.key,
                    supportedEnrol.identifiers.reverse.head.value.replaceAll(" ", ""))
                } else {
                  (
                    supportedServiceName(supportedEnrol.key).getOrElse(
                      throw new RuntimeException(s"service name not found for supported enrolment $supportedEnrol")),
                    supportedEnrol.identifiers.head.key,
                    supportedEnrol.identifiers.head.value.replaceAll(" ", "")
                  )
                }
              }
              .toSeq

          val clientIds = clientIdTypePlusIds ++ maybeNinoForMtdItId(enrols).toSeq

          //APB-4856: Clients with only CGT enrol dont need to go through IV and they should be allowed same as if they have CL >= L200
          val isCgtOnlyClient: Boolean = {
            val enrolKeys: Set[String] = enrols.enrolments.map(_.key)
            enrolKeys.intersect(Service.supportedServices.map(_.enrolmentKey).toSet) == Set(Service.HMRCCGTPD)
          }

          (affinity, confidence) match {
            case (Individual, cl) if cl >= L200 || isCgtOnlyClient => body(clientIds)
            case (Organisation, _)                                 => body(clientIdTypePlusIds)
            case _                                                 => Future successful GenericForbidden
          }
        case _ => Future successful GenericUnauthorized
      } recover handleFailure

  def extractEnrolmentData(enrolls: Set[Enrolment], enrolKey: String, enrolId: String): Option[String] =
    enrolls.find(_.key == enrolKey).flatMap(_.getIdentifier(enrolId)).map(_.value)

  def handleFailure: PartialFunction[Throwable, Result] = {
    case _: NoActiveSession ⇒
      GenericUnauthorized

    case _: InsufficientEnrolments ⇒
      logger.warn(s"Logged in user does not have required enrolments")
      GenericForbidden

    case _: UnsupportedAuthProvider ⇒
      logger.warn(s"user logged in with unsupported auth provider")
      GenericForbidden

    case _: UnsupportedAffinityGroup ⇒
      logger.warn(s"user logged in with unsupported AffinityGroup")
      GenericForbidden
  }
}
