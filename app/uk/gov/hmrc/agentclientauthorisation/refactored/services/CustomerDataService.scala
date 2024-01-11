/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.refactored.services

import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.refactored.connectors.HodCustomerDataConnector
import uk.gov.hmrc.agentservice._
import uk.gov.hmrc.http.HeaderCarrier

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class CustomerDataService @Inject()(connector: HodCustomerDataConnector, appConfig: AppConfig, agentServiceSupport: AgentServiceSupport) {

  private lazy val configMap: Map[String, HodApiConfig] = Map(
    "vat"  -> appConfig.desConfigBundle,
    "itsa" -> appConfig.ifApi1171Bundle
  )

  private def getHodConfigForService(service: String): HodApiConfig =
    configMap.getOrElse(service, throw new Exception(s"$service not supported"))

  def customerDataCheck(service: String, clientId: String, knownFact: Option[String])(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext): Future[CustomerDataCheckResponse] = {

    val apiConfig: HodApiConfig = getHodConfigForService(service)
    val requestConfig = agentServiceSupport.hodRequestConfig(service)(clientId)

    // TODO - impl postRequest
    connector
      .getRequest(apiConfig, requestConfig)
      .map(hodResponse => checkResponse(service)(knownFact, hodResponse))
  }

  private def checkResponse(service: String)(mKnownFact: Option[String], hr: HodResponse[_]): CustomerDataCheckResponse =
    if (hr.customerIsInsolvent.contains(true))
      CustomerDataCheckUnsuccessful(hr.statusCode, "INSOLVENT_CUSTOMER")
    else
      (hr.knownFact, hr.customerName) match {
        case (Some(kf), Some(cn)) =>
          mKnownFact.fold(
            CustomerDataCheckSuccess(
              customerName = cn,
              isUkCustomer = hr.isUkCustomer,
              clientId = hr.clientIdLkUp,
              knownFactSupplied = false
            ): CustomerDataCheckResponse)(
            suppliedKf => {
              if (agentServiceSupport.knownFactCheck(service)(suppliedKf, kf))
                CustomerDataCheckSuccess(
                  customerName = cn,
                  isUkCustomer = hr.isUkCustomer,
                  clientId = hr.clientIdLkUp,
                  knownFactSupplied = true
                )
              else
                CustomerDataCheckUnsuccessful(hr.statusCode, "KNOWN_FACT_CHECK_FAILED")
            }
          )
        case _ => CustomerDataCheckUnsuccessful(hr.statusCode, "CUSTOMER_NAME_EMPTY")
      }

}
