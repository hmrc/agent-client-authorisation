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

package uk.gov.hmrc.agentclientauthorisation.controllers

import org.mongodb.scala.model.Filters
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model.{AgencyEmailNotFound, AgencyNameNotFound, Invitation}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepositoryImpl, MongoAgentReferenceRepository}
import uk.gov.hmrc.agentclientauthorisation.service.ClientNameNotFound
import uk.gov.hmrc.agentclientauthorisation.support.PlatformAnalyticsStubs
import uk.gov.hmrc.agentmtdidentifiers.model.{ClientIdentifier, Service}

import java.time.{LocalDate, LocalDateTime}

class InvitationsReplaceUrnControllerISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val agentReferenceRepo: MongoAgentReferenceRepository = app.injector.instanceOf(classOf[MongoAgentReferenceRepository])
  lazy val invitationsRepo: InvitationsRepositoryImpl = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(agentReferenceRepo.ensureIndexes())
    await(invitationsRepo.ensureIndexes())
    ()
  }

  "POST /invitations/:urn/replace/utr/:utr  " should {

    "return 404 when there is no invitation to replace urn" in {

      val request = FakeRequest("POST", "/invitations/:urn/replace/utr/:utr")
      givenAuditConnector()

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(request))

      status(response) shouldBe 404
    }

    "return 201" in {

      val request = FakeRequest("POST", "/invitations/:urn/replace/utr/:utr")
      givenAuditConnector()
      givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
      getTrustName(utr.value, 200, """{"trustDetails": {"trustName": "Nelson James Trust"}}""")

      val invitation = Invitation.createNew(
        arn,
        Some("personal"),
        Service.TrustNT,
        ClientIdentifier(urn),
        ClientIdentifier(urn),
        None,
        LocalDateTime.now,
        LocalDate.now,
        None
      )
      await(invitationsRepo.collection.insertOne(invitation).toFuture())

      val response = await(controller.replaceUrnInvitationWithUtr(urn, utr)(request))

      status(response) shouldBe 201
      val utrInvitation = await(invitationsRepo.collection.find(Filters.equal("clientId", utr.value)).toFuture())

      utrInvitation.size shouldBe 1
    }

    "throw exception when adding DetailsForEmail failed" when {

      "Agency Email not found" in {
        val request = FakeRequest("POST", "/invitations/:urn/replace/utr/:utr")
        givenAuditConnector()
        givenGetAgencyDetailsStub(arn, Some("name"), None)
        getTrustName(utr.value, 200, """{"trustDetails": {"trustName": "Nelson James Trust"}}""")

        val invitation = Invitation.createNew(
          arn,
          Some("personal"),
          Service.TrustNT,
          ClientIdentifier(urn),
          ClientIdentifier(urn),
          None,
          LocalDateTime.now,
          LocalDate.now,
          None
        )
        await(invitationsRepo.collection.insertOne(invitation).toFuture())

        intercept[AgencyEmailNotFound](await(controller.replaceUrnInvitationWithUtr(urn, utr)(request)))
      }

      "Agency Name not found" in {
        val request = FakeRequest("POST", "/invitations/:urn/replace/utr/:utr")
        givenAuditConnector()
        givenGetAgencyDetailsStub(arn, None, Some("email"))
        getTrustName(utr.value, 200, """{"trustDetails": {"trustName": "Nelson James Trust"}}""")

        val invitation = Invitation.createNew(
          arn,
          Some("personal"),
          Service.TrustNT,
          ClientIdentifier(urn),
          ClientIdentifier(urn),
          None,
          LocalDateTime.now,
          LocalDate.now,
          None
        )
        await(invitationsRepo.collection.insertOne(invitation).toFuture())

        intercept[AgencyNameNotFound](await(controller.replaceUrnInvitationWithUtr(urn, utr)(request)))
      }

      "Client Name not found" in {
        val request = FakeRequest("POST", "/invitations/:urn/replace/utr/:utr")
        givenAuditConnector()
        givenGetAgencyDetailsStub(arn, Some("name"), Some("email"))
        getTrustName(utr.value, 404, "")

        val invitation = Invitation.createNew(
          arn,
          Some("personal"),
          Service.TrustNT,
          ClientIdentifier(urn),
          ClientIdentifier(urn),
          None,
          LocalDateTime.now,
          LocalDate.now,
          None
        )
        await(invitationsRepo.collection.insertOne(invitation).toFuture())

        intercept[ClientNameNotFound](await(controller.replaceUrnInvitationWithUtr(urn, utr)(request)))
      }
    }

  }

}
