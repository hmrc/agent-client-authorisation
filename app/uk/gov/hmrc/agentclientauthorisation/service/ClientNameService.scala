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

package uk.gov.hmrc.agentclientauthorisation.service

import play.api.Logger
import uk.gov.hmrc.agentclientauthorisation.connectors.{CitizenDetailsConnector, DesConnector, EisConnector}
import uk.gov.hmrc.agentmtdidentifiers.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model.{NinoNotFound, VatCustomerDetails}
import uk.gov.hmrc.agentmtdidentifiers.model.{CbcId, CgtRef, MtdItId, PlrId, PptRef, Service, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.{HeaderCarrier, NotFoundException}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

case class ClientNameNotFound() extends Exception

class ClientNameService @Inject()(
  citizenDetailsConnector: CitizenDetailsConnector,
  desConnector: DesConnector,
  eisConnector: EisConnector,
  agentCacheProvider: AgentCacheProvider) {

  private val trustCache = agentCacheProvider.trustResponseCache
  private val cgtCache = agentCacheProvider.cgtSubscriptionCache

  private val logger = Logger(getClass)

  def getClientNameByService(clientId: String, service: Service)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    service match {
      case MtdIt if Nino.isValid(clientId) => getCitizenName(Nino(clientId))
      case MtdIt                           => getItsaTradingName(MtdItId(clientId))
      case PersonalIncomeRecord            => getCitizenName(Nino(clientId))
      case Vat                             => getVatName(Vrn(clientId))
      case Trust                           => getTrustName(clientId)
      case TrustNT                         => getTrustName(clientId)
      case CapitalGains                    => getCgtName(CgtRef(clientId))
      case Ppt                             => getPptCustomerName(PptRef(clientId))
      case Cbc | CbcNonUk                  => getCbcCustomerName(CbcId(clientId))
      case Pillar2                         => getPillar2CustomerName(PlrId(clientId))
    }

  def getItsaTradingName(mtdItId: MtdItId)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    desConnector
      .getNinoFor(mtdItId)
      .flatMap { maybeNino =>
        val nino = maybeNino.getOrElse(throw NinoNotFound())
        desConnector
          .getTradingNameForNino(nino)
          .flatMap {
            case Some(n) if n.nonEmpty => Future.successful(Some(n))
            case _                     => getCitizenName(nino)
          }
      }
      .recover {
        case e => {
          logger.error(s"Unable to translate MtdItId: ${e.getMessage}")
          None
        }
      }

  def getCitizenName(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    citizenDetailsConnector.getCitizenDetails(nino).map(_.flatMap(_.name))

  def getVatName(vrn: Vrn)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    desConnector
      .getVatCustomerDetails(vrn)
      .map { maybeCustomerDetails =>
        val customerDetails = maybeCustomerDetails.getOrElse(VatCustomerDetails(None, None, None))
        customerDetails.tradingName
          .orElse(customerDetails.organisationName)
          .orElse(customerDetails.individual.map(_.name))
      }
      .recover {
        case _: NotFoundException => None
      }

  def getTrustName(trustTaxIdentifier: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    trustCache(trustTaxIdentifier) {
      desConnector.getTrustName(trustTaxIdentifier)
    }.map(_.response).map {
      case Right(trustName) => Some(trustName.name)
      case Left(invalidTrust) =>
        logger.warn(s"error during retrieving trust name for: $trustTaxIdentifier , error: $invalidTrust")
        None
    }

  def getCgtName(cgtRef: CgtRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    cgtCache(cgtRef.value) {
      desConnector.getCgtSubscription(cgtRef)
    }.map(_.response).map {
      case Right(cgtSubscription) =>
        cgtSubscription.subscriptionDetails.typeOfPersonDetails.name match {
          case Right(trusteeName)   => Some(trusteeName.name)
          case Left(individualName) => Some(s"${individualName.firstName} ${individualName.lastName}")
        }
      case Left(e) =>
        logger.warn(s"Error occcured when getting CGT Name")
        None
    }

  def getPptCustomerName(pptRef: PptRef)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    desConnector.getPptSubscription(pptRef).map(_.map(_.customerName))

  def getCbcCustomerName(cbcId: CbcId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    eisConnector.getCbcSubscription(cbcId).map(_.flatMap(_.anyAvailableName))

  def getPillar2CustomerName(plrId: PlrId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    desConnector.getPillar2Subscription(plrId).map(_.response.map(_.organisationName).toOption)
}
