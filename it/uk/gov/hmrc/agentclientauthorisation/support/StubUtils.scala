/*
 * Copyright 2018 HM Revenue & Customs
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

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.libs.json.Json
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model.EmailInformation
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants.nino1
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId, Vrn}
import uk.gov.hmrc.domain._

trait StubUtils {
  me: StartAndStopWireMock =>

  class PreconditionBuilder {

    def agentAdmin(arn: Arn): AgentAdmin =
      AgentAdmin(arn.value)

    def agentAdmin(arn: String, agentCode: String): AgentAdmin =
      AgentAdmin(arn)

    def agentAdmin(arn: Arn, agentCode: AgentCode): AgentAdmin =
      agentAdmin(arn.value, agentCode.value)

    def client(canonicalClientId: TaxIdentifier = MtdItId("mtdItId1"), clientId: ClientId = nino1): Client =
      Client(clientId, canonicalClientId)
  }

  def given() =
    new PreconditionBuilder()

  class BaseUser extends WiremockAware {
    override def wiremockBaseUrl: String = me.wiremockBaseUrl
  }

  case class AgentAdmin(val arn: String) extends BaseUser with AgentAuthStubs {}

  case class Client(val clientId: ClientId, val canonicalClientId: TaxIdentifier)
      extends BaseUser with ClientUserAuthStubs with RelationshipStubs with DesStubs with ASAStubs with EmailStubs
}

trait EmailStubs {
  def givenEmailSent(emailInformation: EmailInformation) = {
    val emailInformationJson = Json.toJson(emailInformation).toString()

    stubFor(
      post(urlEqualTo("/hmrc/email"))
        .withRequestBody(similarToJson(emailInformationJson))
        .willReturn(aResponse().withStatus(202)))

  }
  private def similarToJson(value: String) = equalToJson(value.stripMargin, true, true)

}

trait ASAStubs {
  def givenGetAgentNameViaClient(arn: Arn) = {
    stubFor(get(urlPathEqualTo(s"/agent-services-account/client/agency-name/${arn.value}"))
      .willReturn(aResponse().withStatus(200).withBody(
        s"""{
           |  "agencyName" : "My Agency"
           |}""".stripMargin)))
  }

  def givenMtdItIdToNinoForClient(mtdItId: MtdItId, nino: Nino) = {
    stubFor(get(urlPathEqualTo(s"/agent-services-account/client/mtdItId/${mtdItId.value}"))
      .willReturn(aResponse().withStatus(200).withBody(
        s"""{
           |  "nino" : "${nino.value}"
           |}""".stripMargin)))
  }

  def getTradingName(nino: Nino, tradingName: String) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/trading-name/nino/$nino"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"tradingName": "$tradingName"}""")
        ))

  def getVatClientDetails(vrn: Vrn) =
    stubFor(
      get(urlEqualTo(s"/agent-services-account/client/vat-customer-details/vrn/${vrn.value}"))
        .willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""{"organisationName": "Gadgetron",
                         |"individual" : {
                         |    "title": "Mr",
                         |    "firstName": "Winston",
                         |    "middleName": "H",
                         |    "lastName": "Greenburg"
                         |    },
                         |"tradingName": "GDT"
                         |}""".stripMargin)
        ))
}
