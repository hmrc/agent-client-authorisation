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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import play.api.Environment
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc._
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, CitizenDetailsConnector, DesConnector, EisConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.AgentServicesController.{AgencyNameByArn, AgencyNameByUtr}
import uk.gov.hmrc.agentclientauthorisation.service.BusinessNamesService
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, UpstreamErrorResponse}
import uk.gov.hmrc.play.bootstrap.backend.http.ErrorResponse

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentServicesController @Inject()(
  override val authConnector: AuthConnector,
  val env: Environment,
  desConnector: DesConnector,
  cidConnector: CitizenDetailsConnector,
  eisConnector: EisConnector,
  businessNamesService: BusinessNamesService,
  cc: ControllerComponents)(implicit val appConfig: AppConfig, ec: ExecutionContext, metrics: Metrics)
    extends AuthActions(metrics, appConfig, authConnector, cc) {

  implicit val erFormats: OFormat[ErrorResponse] = Json.format

  def getCurrentAgencyName: Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    getAgencyName(arn)
  }

  def getCurrentAgencyEmail: Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    getAgencyEmail(arn)
  }

  def getCurrentAgencyDetails: Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    getAgencyDetails(arn)
  }

  def getCurrentSuspensionDetails: Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    getAgencySuspensionDetails(arn)
  }

  def getAgencyNameBy(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    if (Arn.isValid(arn.value)) {
      getAgencyName(arn)
    } else errorResponse(BAD_REQUEST, "Invalid Arn")
  }

  def getAgencyEmailBy(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    if (Arn.isValid(arn.value)) {
      getAgencyEmail(arn)
    } else errorResponse(BAD_REQUEST, "Invalid Arn")
  }

  def getSuspensionDetailsBy(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    if (Arn.isValid(arn.value)) {
      getAgencySuspensionDetails(arn)
    } else errorResponse(BAD_REQUEST, "Invalid Arn")
  }

  def getAgencyNameClientWithUtr(utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    if (Utr.isValid(utr.value)) {
      withBasicAuth {
        getAgencyName(utr)
      }
    } else errorResponse(BAD_REQUEST, "Invalid Utr")
  }

  def getBusinessNameUtr(utr: Utr): Action[AnyContent] = Action.async { implicit request =>
    if (Utr.isValid(utr.value)) {
      withBasicAuth {
        businessNamesService.get(utr).map(name => Ok(Json.obj("businessName" -> name)))
      }.recoverWith {
        case e: UpstreamErrorResponse if e.statusCode == 404 =>
          errorResponse(NOT_FOUND, "No business record was matched for the specified UTR")
      }
    } else errorResponse(BAD_REQUEST, "Invalid Utr")
  }

  def getAgencyNames: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[Set[String]]
      .fold(
        error => Future.successful(BadRequest),
        arns => {
          val validationResult = arns.map(Arn.isValid).reduceOption(_ && _).getOrElse(false)

          if (validationResult) {
            withBasicAuth {
              val agencyDetails: Set[Future[Option[AgencyNameByArn]]] = arns.map { arn =>
                agencyNameFor(Arn(arn)).map(nameOpts => nameOpts.map(name => AgencyNameByArn(arn, name)))
              }

              Future
                .sequence(agencyDetails)
                .map(_.flatten)
                .map(details => Ok(Json.toJson(details)))
            }
          } else
            errorResponse(BAD_REQUEST, s"Invalid Arns: (${arns.mkString(",")})")
        }
      )
  }

  def getAgencyNamesUtrs: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[Set[String]]
      .fold(
        error => Future.successful(BadRequest),
        utrs => {

          val validationResult = utrs.map(Utr.isValid).reduceOption(_ && _).getOrElse(false)
          if (validationResult) {
            withBasicAuth {
              val agencyDetails = utrs.map { utr =>
                agencyNameFor(Utr(utr)).map(nameOpts => nameOpts.map(name => AgencyNameByUtr(utr, name)))
              }

              Future
                .sequence(agencyDetails)
                .map(_.flatten)
                .map(details => Ok(Json.toJson(details)))
            }
          } else
            errorResponse(BAD_REQUEST, "Invalid Utr")
        }
      )

  }

  def getBusinessNamesUtrs: Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[Set[String]]
      .fold(
        errors => {
          logger.error(s"Incorrect utr values received: $errors")
          Future.successful(BadRequest)
        },
        utrs => {
          val validationResult = utrs.map(Utr.isValid).reduceOption(_ && _).getOrElse(false)

          if (validationResult) {
            withBasicAuth {
              businessNamesService
                .get(utrs)
                .map(businessNamesByUtrs => Ok(Json.toJson(businessNamesByUtrs)))
            }
          } else
            errorResponse(BAD_REQUEST, "Invalid Utr")
        }
      )
  }

  def getNinoForMtdItId(mtdItId: MtdItId): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      desConnector
        .getNinoFor(mtdItId)
        .map {
          case Some(nino) => Ok(Json.obj("nino" -> nino.value))
          case None =>
            logger.warn("Nino not found for given MtdItId")
            NotFound
        }
    }
  }

  def getMtdItIdForNino(nino: Nino): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      desConnector
        .getMtdIdFor(nino)
        .map {
          case Some(mtdItId) => Ok(Json.obj("mtdItId" -> mtdItId.value))
          case None =>
            logger.warn("MtdItId not found for given nino")
            NotFound
        }
    }
  }

  def getTradingNameForNino(nino: Nino): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      desConnector
        .getTradingNameForNino(nino)
        .flatMap {
          case Some(tn) => Future successful Ok(Json.obj("tradingName" -> tn))
          case None => {
            if (appConfig.altItsaEnabled) cidConnector.getCitizenDetails(nino).map {
              case Some(citizen) => Ok(Json.obj("tradingName" -> (citizen.firstName ++ citizen.lastName).mkString(" ")))
              case None => {
                logger.warn("Citizen not found for given nino (Alt-Itsa)")
                NotFound
              }
            } else {
              logger.warn("Trading name not found for given nino")
              Future successful NotFound
            }
          }
        }
    }
  }

  def getVatCustomerDetails(vrn: Vrn): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      desConnector.getVatCustomerDetails(vrn).map {
        case Some(cd) => Ok(Json.toJson(cd))
        case None =>
          logger.warn("CustomerDetails not found for given vrn")
          NotFound
      }
    }
  }

  def getPptCustomerName(pptRef: PptRef): Action[AnyContent] = Action.async { implicit request =>
    desConnector.getPptSubscription(pptRef).map {
      case Some(record) => Ok(Json.obj("customerName" -> record.customerName))
      case None =>
        logger.warn(s"PPT customer not found for getPptCustomerName")
        NotFound
    }
  }

  def getCbcCustomerName(cbcId: CbcId): Action[AnyContent] = Action.async { implicit request =>
    eisConnector.getCbcSubscription(cbcId).map {
      case Some(subscription) =>
        subscription.anyAvailableName match {
          case Some(name) => Ok(Json.obj("customerName" -> name))
          case None =>
            logger.warn(s"getCbcCustomerName: CBC subscription exists but no names are available for $cbcId")
            Ok(Json.obj("customerName" -> "")) // Unlikely to happen but if it does I don't think we want a hard fail
        }
      case None =>
        logger.warn(s"getCbcCustomerName: CBC subscription not found for $cbcId")
        NotFound
    }
  }

  private def agencyNameFor(identifier: TaxIdentifier)(implicit hc: HeaderCarrier): Future[Option[String]] =
    desConnector
      .getAgencyDetails(identifier)
      .map(_.flatMap(_.agencyDetails.flatMap(_.agencyName)))

  private def getAgencyName(clientIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier) =
    desConnector
      .getAgencyDetails(clientIdentifier)
      .map(_.flatMap(_.agencyDetails.flatMap(_.agencyName)))
      .map {
        case Some(name) => Ok(Json.obj("agencyName" -> name))
        case None       => NoContent
      }

  private def getAgencyEmail(clientIdentifier: TaxIdentifier)(implicit hc: HeaderCarrier) =
    desConnector
      .getAgencyDetails(clientIdentifier)
      .map(_.flatMap(_.agencyDetails.flatMap(_.agencyEmail)))
      .map {
        case Some(email) => Ok(Json.obj("agencyEmail" -> email))
        case None        => NoContent
      }

  private def getAgencyDetails(arn: Arn)(implicit hc: HeaderCarrier) =
    desConnector
      .getAgencyDetails(arn)
      .map(_.flatMap(_.agencyDetails))
      .map {
        case Some(agencyDetails) => Ok(Json.toJson(agencyDetails))
        case None                => NoContent
      }

  private def getAgencySuspensionDetails(arn: Arn)(implicit hc: HeaderCarrier) =
    desConnector
      .getAgencyDetails(arn)
      .map(details => details.flatMap(_.suspensionDetails))
      .map {
        case Some(suspensionDetails) => Ok(Json.toJson(suspensionDetails))
        case None                    => NoContent
      }

  private def errorResponse(code: Int, message: String): Future[Result] = {
    val response = code match {
      case NOT_FOUND   => NotFound(Json.toJson(ErrorResponse(NOT_FOUND, message)))
      case BAD_REQUEST => BadRequest(Json.toJson(ErrorResponse(BAD_REQUEST, message)))
      case _           => throw new InternalServerException("Unsupported error code")
    }

    Future.successful(response)
  }
}

object AgentServicesController {
  case class AgencyNameByArn(arn: String, agencyName: String)

  case class AgencyNameByUtr(utr: String, agencyName: String)

  object AgencyNameByArn {
    implicit val agencyNameFormat: OFormat[AgencyNameByArn] = Json.format
  }

  object AgencyNameByUtr {
    implicit val agencyNameFormat: OFormat[AgencyNameByUtr] = Json.format
  }

  case class BusinessNameByUtr(utr: String, businessName: String)

  object BusinessNameByUtr {
    implicit val businessNameFormat: OFormat[BusinessNameByUtr] = Json.format
  }
}
