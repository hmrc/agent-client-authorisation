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

package uk.gov.hmrc.agentclientauthorisation.controllers

import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, stubFor, urlPathEqualTo}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgencyInvitationsControllerISpec extends UnitSpec with MongoAppAndStubs with AuthStubs {

  lazy val repo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])

  override def beforeEach() {
    super.beforeEach()
    await(repo.ensureIndexes)
  }

  "POST /agencies/references/arn/:arn/clientType/personal" should {
    "return 201 Created with agent link in the LOCATION header if ARN not exists before" in {

      val arn = "SARN2594782"

      await(repo.create(AgentReferenceRecord("SCX39TGT", Arn("LARN7404004"), Seq()))) shouldBe 1

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      stubFor(
        get(urlPathEqualTo(s"/agent-services-account/agent/agency-name")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "agencyName" : "My Agency"
                         |}""".stripMargin)))

      await(repo.findByArn(Arn(arn))) shouldBe None

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/references/arn/$arn/clientType/personal", port).postEmpty()

      response.status shouldBe 201
      response.header(HeaderNames.LOCATION).get should (fullyMatch regex "/invitations/personal/[A-Z0-9]{8}/my-agency")
      await(repo.findByArn(Arn(arn))) shouldBe defined
    }

    "return 201 Created with agent link in the LOCATION header if ARN already exists" in {

      val arn = "QARN5133863"

      await(repo.create(AgentReferenceRecord("SCX39TGA", Arn(arn), Seq()))) shouldBe 1
      await(repo.findByArn(Arn(arn))) shouldBe defined
      await(repo.findBy("SCX39TGA")) shouldBe defined

      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      stubFor(
        get(urlPathEqualTo(s"/agent-services-account/agent/agency-name")).willReturn(
          aResponse()
            .withStatus(200)
            .withBody(s"""
                         |{
                         |  "agencyName" : "My Agency 2"
                         |}""".stripMargin)))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/references/arn/$arn/clientType/personal", port).postEmpty()

      response.status shouldBe 201
      response.header(HeaderNames.LOCATION).get should (fullyMatch regex "/invitations/personal/SCX39TGA/my-agency-2")

      await(repo.findByArn(Arn(arn))) shouldBe defined
      await(repo.findBy("SCX39TGA")) shouldBe defined
    }
  }
}
