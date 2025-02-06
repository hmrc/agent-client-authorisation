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

import play.api.Logging
import play.api.http.Status
import play.api.http.Status.{NOT_FOUND, NO_CONTENT, OK}
import play.api.libs.json.{JsObject, JsValue, Json, Reads}
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.config.AppConfig
import uk.gov.hmrc.agentclientauthorisation.controllers.ClientStatusController.ClientStatus
import uk.gov.hmrc.agentclientauthorisation.model.Invitation.acrReads
import uk.gov.hmrc.agentclientauthorisation.model.InvitationStatusAction.unapply
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.AgentReferenceRecord
import uk.gov.hmrc.agentclientauthorisation.util.HttpAPIMonitor
import uk.gov.hmrc.agentmtdidentifiers.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, Service}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.metrics.Metrics

import java.time.LocalDateTime
import javax.inject._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

@Singleton
class RelationshipsConnector @Inject() (appConfig: AppConfig, http: HttpClient, val metrics: Metrics)(implicit val ec: ExecutionContext)
    extends HttpAPIMonitor with HttpErrorFunctions with Logging {

  private val baseUrl: String = appConfig.agentClientRelationshipsBaseUrl
  private val afiBaseUrl: String = appConfig.afiRelationshipsBaseUrl

  private implicit class FutureResponseOps(f: Future[HttpResponse]) {
    def handleNon2xx(method: String)(implicit ec: ExecutionContext): Future[Unit] = f.map { response =>
      response.status match {
        case status if is2xx(status) => ()
        case other                   => throw UpstreamErrorResponse(s"Unexpected status $other received by '$method'", other)
      }
    }
  }

  def createMtdItRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-MTD-IT-PUT") {
      http
        .PUT[String, HttpResponse](mtdItRelationshipUrl(invitation.arn, invitation.service, invitation.clientId), "")
        .handleNon2xx(s"createMtdItRelationship")
    }

  def createMtdItSuppRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-MTD-IT-SUPP-PUT") {
      http
        .PUT[String, HttpResponse](mtdItRelationshipUrl(invitation.arn, invitation.service, invitation.clientId), "")
        .handleNon2xx(s"createMtdItSuppRelationship")
    }

  def createMtdVatRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-MTD-VAT-PUT") {
      http
        .PUT[String, HttpResponse](mtdVatRelationshipUrl(invitation), "")
        .handleNon2xx(s"createMtdVatRelationship")
    }

  def createAfiRelationship(invitation: Invitation, acceptedDate: LocalDateTime)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] = {
    val body = Json.obj("startDate" -> acceptedDate.toString)
    monitor(s"ConsumedAPI-AgentFiRelationship-relationships-${invitation.service.id}-PUT") {
      http
        .PUT[JsObject, HttpResponse](afiRelationshipUrl(invitation), body)
        .handleNon2xx("createAfiRelationship")
    }
  }

  def createTrustRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-Trust-PUT") {
      http
        .PUT[String, HttpResponse](trustRelationshipUrl(invitation), "")
        .handleNon2xx("createTrustRelationship")
    }

  def createCapitalGainsRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-CapitalGains-PUT") {
      http
        .PUT[String, HttpResponse](cgtRelationshipUrl(invitation), "")
        .handleNon2xx("createCapitalGainsRelationship")
    }

  def createPlasticPackagingTaxRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-PlasticPackagingTax-PUT") {
      http
        .PUT[String, HttpResponse](pptRelationshipUrl(invitation), "")
        .handleNon2xx("createPlasticPackagingTaxRelationship")
    }

  def createCountryByCountryRelationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-CountryByCountry-PUT") {
      http
        .PUT[String, HttpResponse](cbcRelationshipUrl(invitation), "")
        .handleNon2xx("createCountryByCountryRelationship")
    }

  def createPillar2Relationship(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Unit] =
    monitor(s"ConsumedAPI-AgentClientRelationships-relationships-Pillar2-PUT") {
      http
        .PUT[String, HttpResponse](pillar2RelationshipUrl(invitation), "")
        .handleNon2xx("createPillar2Relationship")
    }

  def getActiveRelationships(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Map[String, Seq[Arn]]]] =
    monitor(s"ConsumedAPI-AgentClientRelationships-GetActive-GET") {
      val url = s"$baseUrl/agent-client-relationships/client/relationships/active"
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case status if is2xx(status) => response.json.asOpt[Map[String, Seq[Arn]]]
          case other =>
            logger.warn(s"unexpected error during 'getActiveRelationships', statusCode=$other")
            None
        }
      }
    }

  def checkItsaRelationship(arn: Arn, service: Service, clientId: ClientId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentClientRelationships-${service.id}-GET") {
      val url = mtdItRelationshipUrl(arn, service, clientId)
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case status if is2xx(status) => true
          case status if is4xx(status) => false
          case other =>
            throw UpstreamErrorResponse.apply(s"GET of '$url' returned $other. Response body: '${response.body}'", response.status)

        }
      }
    }

  def deleteItsaRelationship(arn: Arn, service: Service, clientId: ClientId)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Boolean] =
    monitor(s"ConsumedAPI-AgentClientRelationships-${service.id}-DELETE") {
      val url = mtdItRelationshipUrl(arn, service, clientId)
      http.DELETE[HttpResponse](url).map { response =>
        response.status match {
          case status if is2xx(status) => true
          case status if is4xx(status) => false
          case other =>
            throw UpstreamErrorResponse.apply(s"DELETE of '$url' returned $other. Response body: '${response.body}'", response.status)

        }
      }
    }

  def getActiveAfiRelationships(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Seq[JsObject]]] =
    monitor(s"ConsumedAPI-AgentFiRelationship-GetActive-GET") {
      val url = s"$afiBaseUrl/agent-fi-relationship/relationships/active"
      http.GET[HttpResponse](url).map { response =>
        response.status match {
          case status if is2xx(status) => response.json.asOpt[Seq[JsObject]]
          case Status.NOT_FOUND        => Option(Seq.empty)
          case other =>
            logger.error(s"unexpected error during 'getActiveAfiRelationships', statusCode=$other")
            None
        }
      }
    }

  def replaceUrnWithUtr(urn: String, utr: String)(implicit hc: HeaderCarrier): Future[Boolean] = {
    val url = s"$baseUrl/agent-client-relationships/invitations/trusts-enrolment-orchestrator/$urn/update"
    val json = Json.toJson("utr" -> utr)
    monitor("ConsumedAPI-AgentClientRelationships-ReplaceUrnWithUtr-POST") {
      http.POST[JsValue, HttpResponse](url, json).map { response =>
        response.status match {
          case NO_CONTENT => true
          case NOT_FOUND  => false
          case status =>
            throw UpstreamErrorResponse.apply(s"POST of '$url' returned $status. Response body: '${response.body}'", response.status)
        }
      }
    }
  }

  def lookupInvitations(arn: Option[Arn], services: Seq[Service], clientIds: Seq[String], status: Option[InvitationStatus])(implicit
    hc: HeaderCarrier
  ): Future[List[Invitation]] = {
    val url = s"$baseUrl/agent-client-relationships/lookup-invitations"
    val queryParams: List[(String, String)] =
      arn.fold[List[(String, String)]](List.empty)(a => List("arn" -> a.value)) ++
        services.map(svc => "services" -> svc.id) ++
        clientIds.map(id => "clientIds" -> id) ++
        status.fold[List[(String, String)]](List.empty)(statusValue => List("status" -> statusValue.toString))
    monitor("ConsumedAPI-AgentClientRelationships-LookupInvitations-GET") {
      http.GET[HttpResponse](url, queryParams).map { response =>
        response.status match {
          case OK        => response.json.as[List[Invitation]](Reads.list(acrReads))
          case NOT_FOUND => List.empty
          case status =>
            throw UpstreamErrorResponse.apply(s"GET of '$url' returned $status. Response body: '${response.body}'", response.status)
        }
      }
    }
  }

  // Transitional
  def changeACRInvitationStatus(arn: Arn, service: String, clientId: String, changeInvitationStatusRequest: ChangeInvitationStatusRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    monitor(s"ConsumedAPI-AgentClientRelationships-ChangeStatus-PUT") {
      http
        .PUT[ChangeInvitationStatusRequest, HttpResponse](changeACRInvitationStatusUrl(arn, service, clientId), changeInvitationStatusRequest)
    }

  def changeACRInvitationStatusById(invitationId: String, invitationStatusAction: InvitationStatusAction)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[HttpResponse] =
    monitor(s"ConsumedAPI-AgentClientRelationships-ChangeStatusById-PUT") {
      http.PUT[String, HttpResponse](changeACRInvitationStatusByIdUrl(invitationId, invitationStatusAction), "")
    }

  private def trustRelationshipUrl(invitation: Invitation): String =
    invitation.service.enrolmentKey match {
      case Service.HMRCTERSORG =>
        s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-TERS-ORG/client/SAUTR/${encodePathSegment(invitation.clientId.value)}"
      case Service.HMRCTERSNTORG =>
        s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-TERSNT-ORG/client/URN/${encodePathSegment(invitation.clientId.value)}"
    }

  private def mtdItRelationshipUrl(arn: Arn, service: Service, clientId: ClientId): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(arn.value)}/service/${service.id}/client/MTDITID/${encodePathSegment(clientId.value)}"

  private def mtdVatRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-MTD-VAT/client/VRN/${encodePathSegment(invitation.clientId.value)}"

  private def cgtRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-CGT-PD/client/CGTPDRef/${encodePathSegment(invitation.clientId.value)}"

  private def pptRelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-PPT-ORG/client/EtmpRegistrationNumber/${encodePathSegment(
        invitation.clientId.value
      )}"

  private def cbcRelationshipUrl(invitation: Invitation): String = {
    val serviceKey = invitation.service match {
      case Service.Cbc | Service.CbcNonUk => invitation.service.enrolmentKey
      case _                              => throw new IllegalArgumentException
    }
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/$serviceKey/client/cbcId/${encodePathSegment(
        invitation.clientId.value
      )}"
  }

  private def pillar2RelationshipUrl(invitation: Invitation): String =
    s"$baseUrl/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-PILLAR2-ORG/client/PLRID/${encodePathSegment(
        invitation.clientId.value
      )}"

  private def afiRelationshipUrl(invitation: Invitation): String = {
    val arn = encodePathSegment(invitation.arn.value)
    val service = encodePathSegment(invitation.service.id)
    val clientId = encodePathSegment(invitation.clientId.value)
    s"$afiBaseUrl/agent-fi-relationship/relationships/agent/$arn/service/$service/client/$clientId"
  }

  private def changeACRInvitationStatusUrl(arn: Arn, service: String, clientId: String): String =
    s"$baseUrl/agent-client-relationships/transitional/change-invitation-status/arn/${encodePathSegment(arn.value)}/service/$service/client/${encodePathSegment(clientId)}"

  private def changeACRInvitationStatusByIdUrl(invitationId: String, invitationStatusAction: InvitationStatusAction): String =
    s"$baseUrl/agent-client-relationships/authorisation-request/action-invitation/$invitationId/action/${unapply(invitationStatusAction)}"

  def lookupInvitation(invitationId: String)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[Invitation]] =
    monitor("ConsumedAPI-AgentClientRelationships-lookupInvitation-GET") {
      http
        .GET[HttpResponse](s"$baseUrl/agent-client-relationships/lookup-invitation/$invitationId")
        .map { response =>
          response.status match {
            case Status.OK => Some(response.json.as[Invitation](Invitation.acrReads))
            case Status.NOT_FOUND =>
              logger.warn(s"Invitation not found for id: $invitationId")
              None
            case other =>
              logger.error(s"unexpected error during 'lookupInvitation', statusCode=$other")
              None
          }
        }
    }

  def migrateAgentReferenceRecord(record: AgentReferenceRecord)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] =
    monitor("ConsumedAPI-AgentClientRelationships-migrateAgentReferenceRecord-POST") {
      http
        .POST[AgentReferenceRecord, HttpResponse](s"$baseUrl/agent-client-relationships/migrate/agent-reference-record", record)
        .map { response: HttpResponse =>
          response.status match {
            case Status.NO_CONTENT => Some("OK")
            case other =>
              logger.error(s"unexpected error during 'migrateAgentReferenceRecord', statusCode=$other")
              None
          }
        }
    }

  def fetchAgentReferenceById(uid: String)(implicit hc: HeaderCarrier): Future[Option[AgentReferenceRecord]] =
    http
      .GET[HttpResponse](s"$baseUrl/agent-client-relationships/agent-reference/uid/$uid")
      .map { response: HttpResponse =>
        response.status match {
          case OK        => Some(response.json.as[AgentReferenceRecord])
          case NOT_FOUND => None
          case other     => throw UpstreamErrorResponse(s"Agent reference record not found, status: $other", other)
        }
      }

  def fetchOrCreateAgentReference(arn: Arn, normalisedAgentName: String)(implicit hc: HeaderCarrier): Future[AgentReferenceRecord] =
    http
      .PUT[JsObject, HttpResponse](
        s"$baseUrl/agent-client-relationships/agent-reference/${arn.value}",
        Json.obj("normalisedAgentName" -> normalisedAgentName)
      )
      .map { response: HttpResponse =>
        response.status match {
          case OK    => response.json.as[AgentReferenceRecord]
          case other => throw UpstreamErrorResponse(s"Agent reference record could not be found/created, status: $other", other)
        }
      }

  def sendAuthorisationRequest(arn: String, authorisationRequest: AuthorisationRequest)(implicit
    hc: HeaderCarrier,
    ec: ExecutionContext
  ): Future[String] =
    monitor("ConsumedAPI-AgentClientRelationships-createInvitation-POST") {
      http
        .POST[AuthorisationRequest, HttpResponse](s"$baseUrl/agent-client-relationships/agent/$arn/authorisation-request", authorisationRequest)
        .map { response: HttpResponse =>
          response.status match {
            case Status.CREATED => response.json.as[AuthorisationResponse].invitationId
            case other =>
              Try(Json.parse(response.body)) match {
                case Success(json) =>
                  (json \ "code").asOpt[String] match {
                    case Some(code) =>
                      throw UpstreamErrorResponse(
                        s"Error when creating invitation on agent-client-relationships, httpStatus=$other, errorCode=$code",
                        other
                      )
                    case None =>
                      throw UpstreamErrorResponse(s"Error when creating invitation on agent-client-relationships, httpStatus=$other", other)
                  }
                case Failure(error) =>
                  throw UpstreamErrorResponse(
                    s"Error when creating invitation on agent-client-relationships, httpStatus=$other, error=${error.getMessage}",
                    other
                  )
              }
          }
        }
    }

  def migratePartialAuthRecordToAcr(invitation: Invitation)(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Option[String]] = {
    implicit val rds: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
      override def read(method: String, url: String, response: HttpResponse): HttpResponse = response
    }
    monitor("ConsumedAPI-AgentClientRelationships-migratePartialAuthRecord-POST") {
      http
        .POST[Invitation, HttpResponse](s"$baseUrl/agent-client-relationships/migrate/partial-auth-record", invitation)(
          Invitation.acrWrites,
          rds,
          hc,
          ec
        )
        .map { response: HttpResponse =>
          response.status match {
            case Status.NO_CONTENT => Some("OK")
            case other =>
              logger.error(s"unexpected error during 'migratePartialAuthRecord', statusCode=$other")
              None
          }
        }
    }
  }

  def getCustomerStatus(implicit hc: HeaderCarrier): Future[ClientStatus] =
    monitor("ConsumedAPI-AgentClientRelationships-getCustomerStatus-GET") {
      http
        .GET[HttpResponse](s"$baseUrl/agent-client-relationships/customer-status")
        .map { response: HttpResponse =>
          response.status match {
            case OK => response.json.as[ClientStatus]
            case other =>
              throw UpstreamErrorResponse(s"unexpected error during 'getCustomerStatus', statusCode=$other", other)
          }
        }
    }

}
