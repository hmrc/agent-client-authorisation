/*
 * Copyright 2017 HM Revenue & Customs
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
import javax.inject._

import org.joda.time.DateTime
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.agentclientauthorisation.UriPathEncoding.encodePathSegment
import uk.gov.hmrc.agentclientauthorisation.model.Invitation
import uk.gov.hmrc.http.{HeaderCarrier, HttpPut, HttpResponse}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._

import scala.concurrent.Future

@Singleton
class RelationshipsConnector @Inject() (@Named("relationships-baseUrl") baseUrl: URL,
                                        @Named("afi-relationships-baseUrl") afiBaseUrl: URL, httpPut: HttpPut) {

  def createRelationship(invitation: Invitation)(implicit hc: HeaderCarrier): Future[Unit] =
    httpPut.PUT[String, HttpResponse](relationshipUrl(invitation).toString, "") map (_ => Unit)

  def createAfiRelationship(invitation: Invitation, acceptedDate: DateTime)(implicit hc: HeaderCarrier): Future[Unit] = {
    val body = Json.obj("startDate" -> acceptedDate)
    val string = body.toString()
    httpPut.PUT[JsObject, HttpResponse](afiRelationshipUrl(invitation).toString, body) map (_ => Unit)
  }

  private def relationshipUrl(invitation: Invitation): URL = new URL(baseUrl,
    s"/agent-client-relationships/agent/${encodePathSegment(invitation.arn.value)}/service/HMRC-MTD-IT/client/MTDITID/${encodePathSegment(invitation.clientId)}")

  private def afiRelationshipUrl(invitation: Invitation): URL = {
    val arn = encodePathSegment(invitation.arn.value)
    val service = encodePathSegment(invitation.service.id)
    val clientId = encodePathSegment(invitation.clientId)
    new URL(afiBaseUrl, s"/agent-fi-relationship/relationships/agent/$arn/service/$service/client/$clientId")
  }
}

