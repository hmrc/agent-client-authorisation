/*
 * Copyright 2016 HM Revenue & Customs
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

import play.api.hal.{Hal, HalLink, HalLinks, HalResource}
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.Action
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthConnector, UserDetails, UserDetailsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.HalWriter.halWriter
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.AuthActions
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationHttpRequest, AgentClientAuthorisationRequest, EnrichedAgentClientAuthorisationRequest}
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.agentclientauthorisation.sa.services.SaLookupService
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class AuthorisationRequestController(authorisationRequestRepository: AuthorisationRequestRepository, saLookupService: SaLookupService, override val authConnector: AuthConnector, userDetailsConnector: UserDetailsConnector)
  extends BaseController with AuthActions {

  def createRequest() = onlyForSaAgents.async(parse.json) { implicit request =>
    withJsonBody[AgentClientAuthorisationHttpRequest] { authRequest: AgentClientAuthorisationHttpRequest =>
      saLookupService.utrAndPostcodeMatch(SaUtr(authRequest.clientRegimeId), authRequest.clientPostcode) flatMap { utrAndPostcodeMatch =>
        if (utrAndPostcodeMatch) {
          // TODO Audit
          if (authRequest.regime == "sa") {
            authorisationRequestRepository.create(request.agentCode, authRequest.regime, authRequest.clientRegimeId, request.userDetailsLink) map { _ => Created }
          }
          else {
            Future successful NotImplemented(Json.obj("message" -> s"This service does not currently support the '${authRequest.regime}' tax regime"))
          }
        } else {
          // TODO Audit failure including UTR and postcode
          Future successful Forbidden("No SA taxpayer found with the given UTR and postcode")
        }
      }
    }
  }

  def getRequests() = onlyForSaAgents.async { implicit request =>
    authorisationRequestRepository.list(request.agentCode).flatMap(enrich).map(toHalResource).map(Ok(_)(halWriter))
  }

  def acceptRequest(requestId: String) = onlyForSaClients.async { implicit request =>
    Future successful Ok
  }

  private def enrich(requests: List[AgentClientAuthorisationRequest])(implicit hc: HeaderCarrier): Future[List[EnrichedAgentClientAuthorisationRequest]] = {
    val eventuallyNames = namesByUtr(requests.map(_.clientSaUtr).distinct)
    val eventuallyAgentDetails = userDetails(requests)
    for {
      names <- eventuallyNames
      agentUserDetails <-  eventuallyAgentDetails
      requestsWithNames = requests zip agentUserDetails
    } yield {
      requestsWithNames map(r => enrich(r._1, names, r._2))
    }
  }

  def userDetails(requests: List[AgentClientAuthorisationRequest])(implicit hc: HeaderCarrier): Future[List[UserDetails]] = {
    Future sequence requests.map(r => userDetailsConnector.userDetails(r.agentUserDetailsLink))
  }

  private def enrich(request: AgentClientAuthorisationRequest, names: Map[SaUtr, Option[String]], agentUserDetails: UserDetails): EnrichedAgentClientAuthorisationRequest = {
    EnrichedAgentClientAuthorisationRequest(
      id = request.id.stringify,
      agentCode = request.agentCode,
      regime = request.regime,
      clientRegimeId = request.clientRegimeId,
      clientFullName = names(request.clientSaUtr),
      agentName = agentUserDetails.agentName,
      agentFriendlyName = agentUserDetails.agentFriendlyName,
      events = request.events)
  }

  private def namesByUtr(utrs: List[SaUtr])(implicit hc: HeaderCarrier): Future[Map[SaUtr, Option[String]]] = {
    val eventuallyNames = Future sequence (utrs.map(saLookupService.lookupByUtr))
    eventuallyNames.map({ nameList =>
      (utrs zip nameList).toMap
    })
  }

  private def toHalResource(requests: List[EnrichedAgentClientAuthorisationRequest]): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource).toVector

    Hal.embedded("requests", requestResources:_*) ++
      HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests().url)
  }

  private def toHalResource(request: EnrichedAgentClientAuthorisationRequest): HalResource = {
    val links = HalLinks(Vector(HalLink("self", s"${uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests().url}/${request.id}")))
    HalResource(links, Json.toJson(request).as[JsObject])
  }

}
