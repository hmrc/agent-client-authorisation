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

package uk.gov.hmrc.agentclientauthorisation.service

import javax.inject.Inject
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgentServicesAccountConnector, Citizen, CitizenDetailsConnector, DesConnector}
import uk.gov.hmrc.agentclientauthorisation.model.Service._
import uk.gov.hmrc.agentclientauthorisation.model.{Service, TrustDetailsResponse}
import uk.gov.hmrc.agentmtdidentifiers.model.{MtdItId, Utr, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

case class ClientNameNotFound() extends Exception

class ClientNameService @Inject()(
  agentServicesAccountConnector: AgentServicesAccountConnector,
  citizenDetailsConnector: CitizenDetailsConnector,
  desConnector: DesConnector) {

  def getClientNameByService(clientId: String, service: Service)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[Option[String]] =
    service.id match {
      case HMRCMTDIT if MtdItId.isValid(clientId) => getItsaTradingName(MtdItId(clientId))
      case HMRCPIR if Nino.isValid(clientId)      => getCitizenName(Nino(clientId))
      case HMRCMTDVAT if Vrn.isValid(clientId)    => getVatName(Vrn(clientId))
      case HMRCTERSORG if Utr.isValid(clientId)   => getTrustName(Utr(clientId))
      case _                                      => Future successful None
    }

  def getItsaTradingName(mtdItId: MtdItId)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    agentServicesAccountConnector.getNinoForMtdItId(mtdItId).flatMap {
      case Some(nino) =>
        agentServicesAccountConnector
          .getTradingName(nino)
          .flatMap {
            case name if name.isDefined => Future successful name
            case None                   => getCitizenName(nino)
          }
      case None => throw new Exception(s"No corresponding Nino for this MtdItId: $mtdItId")
    }

  def getCitizenName(nino: Nino)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    getCitizenRecord(nino).map(_.name)

  def getCitizenRecord(nino: Nino)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Citizen] =
    citizenDetailsConnector.getCitizenDetails(nino)

  def getVatName(vrn: Vrn)(implicit c: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    agentServicesAccountConnector.getCustomerDetails(vrn).map { customerDetails =>
      customerDetails.tradingName
        .orElse(customerDetails.organisationName)
        .orElse(customerDetails.individual.map(_.name))
    }

  def getTrustName(utr: Utr)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    for {
      trustDetails: Option[TrustDetailsResponse] <- desConnector.getTrustDetails(utr)
      trustName = trustDetails.map(_.trustDetails.trustName)
    } yield trustName

}
