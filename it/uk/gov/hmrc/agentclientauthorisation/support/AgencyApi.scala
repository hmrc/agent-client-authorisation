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

package uk.gov.hmrc.agentclientauthorisation.support

import play.mvc.Http.HeaderNames._
import uk.gov.hmrc.agentclientauthorisation.model.{Arn, MtdClientId}
import uk.gov.hmrc.agentclientauthorisation.support.HalTestHelpers.HalResourceHelper
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.http.logging.SessionId

class AgencyApi(arn: Arn, port: Int) {

  private val getInvitationsUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent"
  private val getInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations/sent/"
  private val createInvitationUrl = s"/agent-client-authorisation/agencies/${arn.arn}/invitations"

  implicit val hc: HeaderCarrier = HeaderCarrier(sessionId = Some(SessionId(arn.arn)))

  def sendInvitation(clientId: MtdClientId, regime: String = "mtd-sa", postcode:String = "AA1 1AA"): String = {

    val response = new Resource(createInvitationUrl, port).postAsJson(
      s"""{"regime": "$regime", "clientId": "${clientId.value}", "postcode": "$postcode"}"""
    )(hc)

    require(response.status == 201, s"creating an invitation should return 201, was [${response.status}]")
    response.header(LOCATION).get
  }

  def sentInvitations(filteredBy:Seq[(String, String)] = Nil): HalResourceHelper = {

    val params = withFilterParams(filteredBy)
    val response: HttpResponse = new Resource(getInvitationsUrl+params, port).get()(hc)
    require(response.status == 200, s"Couldn't get invitations, response status [${response.status}]")
    HalTestHelpers(response.json)
  }

  def withFilterParams(filteredBy: Seq[(String, String)]): String = {
    filteredBy match {
      case Nil => ""
      case (k, v) :: Nil => s"?$k=$v"
      case (k, v) :: tail => s"?$k=$v" + tail.map(params => s"&${params._1}=${params._2}").mkString
    }
  }
}
