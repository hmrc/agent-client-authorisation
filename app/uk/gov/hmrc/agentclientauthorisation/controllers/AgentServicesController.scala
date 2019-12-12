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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.kenshoo.play.metrics.Metrics
import javax.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc._
import play.api.{Environment, Logger}
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthActions, DesConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.AgentServicesController.{AgencyNameByArn, AgencyNameByUtr, BusinessNameByUtr}
import uk.gov.hmrc.agentclientauthorisation.model.AgencyNameNotFound
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Utr, Vrn}
import uk.gov.hmrc.auth.core.AuthConnector
import uk.gov.hmrc.domain.{Nino, TaxIdentifier}
import uk.gov.hmrc.http.{HeaderCarrier, InternalServerException, NotFoundException}
import uk.gov.hmrc.play.bootstrap.http.ErrorResponse

import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AgentServicesController @Inject()(
  override val authConnector: AuthConnector,
  val env: Environment,
  desConnector: DesConnector,
  cc: ControllerComponents)(implicit val appConfig: AppConfig, ec: ExecutionContext, metrics: Metrics)
    extends AuthActions(metrics, authConnector, cc) {

  implicit val erFormats: OFormat[ErrorResponse] = Json.format

  def getCurrentAgencyName: Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    getAgencyName(arn)
  }

  def getCurrentAgencyEmail: Action[AnyContent] = onlyForAgents { implicit request => implicit arn =>
    getAgencyEmail(arn)
  }

  def getAgencyNameBy(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    if (Arn.isValid(arn.value)) {
      withBasicAuth {
        getAgencyName(arn)
      }
    } else errorResponse(BAD_REQUEST, "Invalid Arn")
  }

  def getAgencyEmailBy(arn: Arn): Action[AnyContent] = Action.async { implicit request =>
    if (Arn.isValid(arn.value)) {
      withBasicAuth {
        getAgencyEmail(arn)
      }
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
        businessNameFor(utr).map(name => Ok(Json.obj("businessName" -> name)))
      }.recoverWith {
        case e: NotFoundException => errorResponse(NOT_FOUND, "No business record was matched for the specified UTR")
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
              val agencyDetails = arns.map { arn =>
                agencyNameFor(Arn(arn)).map(AgencyNameByArn(arn, _))
              }

              Future.sequence(agencyDetails).map(details => Ok(Json.toJson(details)))
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
                agencyNameFor(Utr(utr)).map(AgencyNameByUtr(utr, _))
              }
              Future.sequence(agencyDetails).map(details => Ok(Json.toJson(details)))
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
        error => Future.successful(BadRequest),
        utrs => {
          val validationResult = utrs.map(Utr.isValid).reduceOption(_ && _).getOrElse(false)
          val defaultName = ""

          if (validationResult) {
            withBasicAuth {
              val businessRecord = utrs.map { utr =>
                businessNameFor(Utr(utr))
                  .recover {
                    case e: NotFoundException =>
                      Logger(getClass).warn("An error happened when trying to get business name for a utr", e)
                      defaultName
                  }
                  .map(BusinessNameByUtr(utr, _))
              }
              Future.sequence(businessRecord).map(details => Ok(Json.toJson(details)))
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
            Logger.warn("Nino not found for given MtdItId")
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
            Logger.warn("MtdItId not found for given nino")
            NotFound
        }
    }
  }

  def getTradingNameForNino(nino: Nino): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      desConnector
        .getTradingNameForNino(nino)
        .map {
          case Some(tn) => Ok(Json.obj("tradingName" -> tn))
          case None =>
            Logger.warn("Trading name not found for given nino")
            NotFound
        }
    }
  }

  def getVatCustomerDetails(vrn: Vrn): Action[AnyContent] = Action.async { implicit request =>
    withBasicAuth {
      desConnector.getVatCustomerDetails(vrn).map {
        case Some(cd) => Ok(Json.toJson(cd))
        case None =>
          Logger.warn("CustomerDetails not found for given vrn")
          NotFound
      }
    }
  }

  private def agencyNameFor(identifier: TaxIdentifier)(implicit hc: HeaderCarrier): Future[String] =
    desConnector
      .getAgencyDetails(identifier)
      .map(_.flatMap(_.agencyDetails.flatMap(_.agencyName)))
      .map {
        case Some(name) => name
        case None       => throw AgencyNameNotFound()
      }

  private def businessNameFor(utr: Utr)(implicit hc: HeaderCarrier): Future[String] = {
    val defaultName = ""
    desConnector.getBusinessName(utr).map { _.getOrElse(defaultName) }
  }

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
