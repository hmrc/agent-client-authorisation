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
import org.joda.time.{DateTime, LocalDate}
import play.api.libs.json.JsObject
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.agentclientauthorisation.model.{ClientIdentifier, Invitation, MtdItIdType, Service}
import uk.gov.hmrc.agentclientauthorisation.repository.{AgentReferenceRecord, InvitationsRepository, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.support.{MongoAppAndStubs, Resource}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HttpResponse
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class AgencyInvitationsControllerISpec extends UnitSpec with MongoAppAndStubs with AuthStubs {

  lazy val agentReferenceRepo = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepository])

  override def beforeEach() {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes)
  }

  "POST /agencies/references/arn/:arn/clientType/personal" should {
    "return 201 Created with agent link in the LOCATION header if ARN not exists before" in {

      val arn = "SARN2594782"

      await(agentReferenceRepo.create(AgentReferenceRecord("SCX39TGT", Arn("LARN7404004"), Seq()))) shouldBe 1

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

      await(agentReferenceRepo.findByArn(Arn(arn))) shouldBe None

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/references/arn/$arn/clientType/personal", port).postEmpty()

      response.status shouldBe 201
      response.header(HeaderNames.LOCATION).get should (fullyMatch regex "/invitations/personal/[A-Z0-9]{8}/my-agency")
      await(agentReferenceRepo.findByArn(Arn(arn))) shouldBe defined
    }

    "return 201 Created with agent link in the LOCATION header if ARN already exists" in {

      val arn = "QARN5133863"

      await(agentReferenceRepo.create(AgentReferenceRecord("SCX39TGA", Arn(arn), Seq()))) shouldBe 1
      await(agentReferenceRepo.findByArn(Arn(arn))) shouldBe defined
      await(agentReferenceRepo.findBy("SCX39TGA")) shouldBe defined

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

      await(agentReferenceRepo.findByArn(Arn(arn))) shouldBe defined
      await(agentReferenceRepo.findBy("SCX39TGA")) shouldBe defined
    }
  }

  "GET /agencies/:arn/invitations/sent/:invitationId" should {

    val arn = "TARN0000001"
    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)

    "return 200 with an invitation entity" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val invitation = await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/agencies/$arn/invitations/sent/${invitation.invitationId.value}",
          port).get()

      response.status shouldBe 200

      val json = response.json.as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn
      (json \ "clientType").as[String] shouldBe "personal"
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe "FOO"
      (json \ "clientIdType").as[String] shouldBe "MTDITID"
      (json \ "suppliedClientId").as[String] shouldBe "FOO"
      (json \ "suppliedClientIdType").as[String] shouldBe "MTDITID"
    }

    "return 403 when invitation is for another agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent("TARN0000002")

      val invitation = await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/agencies/TARN0000002/invitations/sent/${invitation.invitationId.value}",
          port).get()

      response.status shouldBe 403
    }

    "return 403 when arn is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val invitation = await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/agencies/TARN0000002/invitations/sent/${invitation.invitationId.value}",
          port).get()

      response.status shouldBe 403
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenNotAuthorised()

      val invitation = await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(
          s"/agent-client-authorisation/agencies/$arn/invitations/sent/${invitation.invitationId.value}",
          port).get()

      response.status shouldBe 401
    }

    "return 404 when invitation does not exist" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent/XXXXXXXXXXXXX", port).get()

      response.status shouldBe 404
    }
  }

  "GET /agencies/:arn/invitations/sent" should {

    val arn = "TARN0000001"
    val clientIdentifier = ClientIdentifier("FOO", MtdItIdType.id)

    "return 200 with an invitation entity" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val invitation = await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent", port).get()

      response.status shouldBe 200

      val jsonResponse = response.json.as[JsObject]
      val json = (jsonResponse \ "_embedded" \ "invitations" \ 0).as[JsObject]
      (json \ "invitationId").as[String] shouldBe invitation.invitationId.value
      (json \ "arn").as[String] shouldBe arn
      (json \ "clientType").as[String] shouldBe "personal"
      (json \ "status").as[String] shouldBe "Pending"
      (json \ "clientId").as[String] shouldBe "FOO"
      (json \ "clientIdType").as[String] shouldBe "MTDITID"
      (json \ "suppliedClientId").as[String] shouldBe "FOO"
      (json \ "suppliedClientIdType").as[String] shouldBe "MTDITID"
    }

    "return 200 with empty invitation array when no invitations are found for this arn" in {
      givenAuditConnector()
      givenAuthorisedAsAgent("TARN0000002")

      await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/TARN0000002/invitations/sent", port).get()

      response.status shouldBe 200
      val jsonResponse = response.json.as[JsObject]
      val json = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      json shouldBe empty
    }

    "return 403 when arn is not of current agent" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/TARN0000002/invitations/sent", port).get()

      response.status shouldBe 403
    }

    "return 401 when agent is not authorised" in {
      givenAuditConnector()
      givenNotAuthorised()

      await(
        invitationsRepo.create(
          Arn(arn),
          Some("personal"),
          Service.MtdIt,
          clientIdentifier,
          clientIdentifier,
          DateTime.now(),
          LocalDate.now()))

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent", port).get()

      response.status shouldBe 401
    }

    "return 200 with empty invitation array when no invitations are found" in {
      givenAuditConnector()
      givenAuthorisedAsAgent(arn)

      val response: HttpResponse =
        new Resource(s"/agent-client-authorisation/agencies/$arn/invitations/sent", port).get()

      response.status shouldBe 200
      val jsonResponse = response.json.as[JsObject]
      val json = (jsonResponse \ "_embedded" \ "invitations").as[Seq[JsObject]]

      json shouldBe empty
    }
  }
}
