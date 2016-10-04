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
import uk.gov.hmrc.agentclientauthorisation.connectors.{AuthConnector, UserDetailsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.HalWriter.halWriter
import uk.gov.hmrc.agentclientauthorisation.controllers.actions.{AgentRequest, AuthActions, SaClientRequest}
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestRepository
import uk.gov.hmrc.agentclientauthorisation.sa.services.SaLookupService
import uk.gov.hmrc.domain.SaUtr
import uk.gov.hmrc.play.microservice.controller.BaseController

import scala.concurrent.Future

class AuthorisationRequestController(authorisationRequestRepository: AuthorisationRequestRepository, saLookupService: SaLookupService, override val authConnector: AuthConnector, userDetailsConnector: UserDetailsConnector)
  extends BaseController with AuthActions {
  val regimes = Seq("sa", "ct")

  def createRequest() = onlyForSaAgents.async(parse.json) { implicit request =>
    withJsonBody[AgentClientAuthorisationHttpRequest] { authRequest: AgentClientAuthorisationHttpRequest =>
      saLookupService.lookupNameByUtrAndPostcode(SaUtr(authRequest.customerRegimeId), authRequest.postcode) flatMap {
        case Some(name) if regimes.contains(authRequest.regime) => authorisationRequestRepository.create(authRequest.arn, authRequest.regime, authRequest.customerRegimeId, authRequest.postcode) map { _ => Created }
        case Some(name) if !regimes.contains(authRequest.regime) => Future successful NotImplemented(Json.obj("message" -> s"This service does not currently support the '${authRequest.regime}' tax regime"))
        case None => Future successful Forbidden(s"No ${authRequest.regime} taxpayer found with the given UTR and postcode")
      }
    }
  }

  def getRequests() = saClientsOrAgents.async { implicit request =>
    // TODO ARN needs to come from request somehow
    request match {
      case AgentRequest(agentCode, _, _) =>
        authorisationRequestRepository.list(Arn(agentCode.value)).map(toHalResource).map(Ok(_)(halWriter))
      case SaClientRequest(saUtr, _) =>
        authorisationRequestRepository.list("sa", saUtr.value).map(toHalResource).map(Ok(_)(halWriter))
    }
  }

  def getRequest(requestId: String) = onlyForSaClients.async { implicit request =>
    val id = BSONObjectID(requestId)
    authorisationRequestRepository.findById(id).map {
      case Some(r) if r.customerSaUtr.get == request.saUtr => Ok(toHalResource(r))(halWriter)
      case Some(r) => Forbidden
      case None => NotFound
    }
  }

  def acceptRequest(requestId: String) = updateRequestAsClient(requestId, model.Accepted)

  def rejectRequest(requestId: String) = updateRequestAsClient(requestId, model.Rejected)

  private def updateRequestAsClient(requestId: String, newStatus: InvitationStatus) = onlyForSaClients.async { implicit request =>
    val id = BSONObjectID(requestId)
    authorisationRequestRepository.findById(id).flatMap {
      case Some(r) if r.customerSaUtr.get == request.saUtr && r.events.last.status == model.Pending => authorisationRequestRepository.update(id, newStatus).map(_ => Ok)
      case Some(r) => Future successful Forbidden
      case None => Future successful NotFound
    }
  }

  private def toHalResource(requests: List[Invitation]): HalResource = {
    val requestResources: Vector[HalResource] = requests.map(toHalResource).toVector

    Hal.embedded("requests", requestResources:_*) ++
      HalLink("self", uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests().url)
  }

  private def toHalResource(request: Invitation): HalResource = {
    val links = HalLinks(Vector(HalLink("self", s"${uk.gov.hmrc.agentclientauthorisation.controllers.routes.AuthorisationRequestController.getRequests().url}/${request.id.stringify}")))
    HalResource(links, Json.toJson(request).as[JsObject])
  }
}
