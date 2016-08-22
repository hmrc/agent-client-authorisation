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
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthConnector, UserDetails, UserDetailsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.HalWriter.halWriter
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{AgentRequest, AuthActions, SaClientRequest}
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model.{AgentClientAuthorisationHttpRequest, AgentClientAuthorisationRequest, AuthorisationStatus, EnrichedAgentClientAuthorisationRequest}
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
      saLookupService.lookupNameByUtrAndPostcode(SaUtr(authRequest.clientRegimeId), authRequest.clientPostcode) flatMap {
        case Some(name) if authRequest.regime == "sa" => authorisationRequestRepository.create(request.agentCode, authRequest.regime, authRequest.clientRegimeId, request.userDetailsLink) map { _ => Created }
        case Some(name) if authRequest.regime != "sa" => Future successful NotImplemented(Json.obj("message" -> s"This service does not currently support the '${authRequest.regime}' tax regime"))
        case None => Future successful Forbidden("No SA taxpayer found with the given UTR and postcode")
      }
    }
  }

  def getRequests() = saClientsOrAgents.async { implicit request =>
    request match {
      case AgentRequest(agentCode, _, _) =>
        authorisationRequestRepository.list(agentCode).flatMap(toEnrichedRequest).map(toHalResource).map(Ok(_)(halWriter))
      case SaClientRequest(saUtr, _) =>
        authorisationRequestRepository.list("sa", saUtr.value).flatMap(toEnrichedRequest).map(toHalResource).map(Ok(_)(halWriter))
    }
  }

  def getRequest(requestId: String) = onlyForSaClients.async { implicit request =>
    val id = BSONObjectID(requestId)
    authorisationRequestRepository.findById(id).flatMap {
      case Some(r) if r.clientSaUtr == request.saUtr => toEnrichedRequest(List(r)).map(_.head).map(toHalResource).map(Ok(_)(halWriter))
      case Some(r) => Future successful Forbidden
      case None => Future successful NotFound
    }
  }

  def acceptRequest(requestId: String) = updateRequestAsClient(requestId, model.Accepted)

  def rejectRequest(requestId: String) = updateRequestAsClient(requestId, model.Rejected)

  private def updateRequestAsClient(requestId: String, newStatus: AuthorisationStatus) = onlyForSaClients.async { implicit request =>
    val id = BSONObjectID(requestId)
    authorisationRequestRepository.findById(id).flatMap {
      case Some(r) if r.clientSaUtr == request.saUtr && r.events.last.status == model.Pending => authorisationRequestRepository.update(id, newStatus).map(_ => Ok)
      case Some(r) => Future successful Forbidden
      case None => Future successful NotFound
    }
  }

  private def toEnrichedRequest(requests: List[AgentClientAuthorisationRequest])(implicit hc: HeaderCarrier): Future[List[EnrichedAgentClientAuthorisationRequest]] = {
    val eventuallyNames = saLookupService lookupNamesByUtr requests.map(_.clientSaUtr).distinct
    val eventuallyAgentDetails = userDetails(requests)
    for {
      names <- eventuallyNames
      agentUserDetails <-  eventuallyAgentDetails
      requestsWithNames = requests zip agentUserDetails
    } yield {
      requestsWithNames map(r => EnrichedAgentClientAuthorisationRequest.from(r._1, names, r._2))
    }
  }

  private def userDetails(requests: List[AgentClientAuthorisationRequest])(implicit hc: HeaderCarrier): Future[List[UserDetails]] = {
    Future sequence requests.map(r => userDetailsConnector.userDetails(r.agentUserDetailsLink))
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
