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

import org.apache.pekko.stream.Materializer
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.ErrorResults.InvitationNotFound
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support.{PlatformAnalyticsStubs, TestHalResponseInvitation}
import uk.gov.hmrc.agentmtdidentifiers.model.Service.MtdIt
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, ClientIdentifier, InvitationId, Service}

import java.time.temporal.ChronoUnit.MILLIS
import java.time.{Instant, LocalDate, LocalDateTime, ZoneOffset}
import scala.concurrent.Future

class AgencyAlternativeItsaEMAControllerISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  implicit val mat: Materializer = app.injector.instanceOf[Materializer]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(invitationsRepo.ensureIndexes())
    ()
  }

  "PUT /alt-itsa/HMRC-MTD-IT/update/:nino" should {

    val request = FakeRequest("PUT", "/alt-itsa/update/:nino").withHeaders("Authorization" -> "Bearer testtoken")

    "return 204 when there are no alternative itsa invitations found for the client" in {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 204
    }

    "return 500 when DES MtdItId call fails leaving alt-itsa invitation untouched" in {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 500
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when DES MtdItId call return 404 (client not signed up to MtdItId) leaving alt-itsa invitation untouched" in {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      givenMtdItIdIsUnknownFor(nino)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when client has MtdItId, updating the invitation store to replace Nino" in {

      val altItsaPending1 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2.copy(clientId = mtdItId))
    }

    "return 201 (Created) when there is PartialAuth invitation and ETMP create relationship call succeeds" in {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 500 when there is PartialAuth invitation but ETMP create relationship call fails (Nino will be replaced)" in {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationshipFails(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 500
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe PartialAuth
    }

    "return 201 when there is PartialAuth but clientId is MTDITID (as if ETMP create relationship call failed previously)" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 404 when there is a PartialAuth existing for a Supporting Agent" should {}
  }

  "PUT /alt-itsa/HMRC-MTD-IT-SUPP/update/:nino" should {

    val request = FakeRequest("PUT", "/alt-itsa/update/:nino").withHeaders("Authorization" -> "Bearer testtoken")

    "return 204 when there are no alternative itsa invitations found for the client" in {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdItSupp,
          mtdItId,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 204
    }

    "return 500 when DES MtdItId call fails leaving alt-itsa invitation untouched" in {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            LocalDateTime.now().truncatedTo(MILLIS),
            LocalDate.now().plusDays(21),
            None
          )
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 500
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when DES MtdItId call return 404 (client not signed up to MtdItId) leaving alt-itsa invitation untouched" in {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            LocalDateTime.now().truncatedTo(MILLIS),
            LocalDate.now().plusDays(21),
            None
          )
      )

      givenMtdItIdIsUnknownFor(nino)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when client has MtdItId, updating the invitation store to replace Nino" in {

      val altItsaPending1 = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            LocalDateTime.now().truncatedTo(MILLIS),
            LocalDate.now().plusDays(21),
            None
          )
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2.copy(clientId = mtdItId))
    }

    "return 201 (Created) when there is PartialAuth invitation and ETMP create relationship call succeeds" in {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            LocalDateTime.now().truncatedTo(MILLIS),
            LocalDate.now().plusDays(21),
            None
          )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 500 when there is PartialAuth invitation but ETMP create relationship call fails (Nino will be replaced)" in {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            LocalDateTime.now().truncatedTo(MILLIS),
            LocalDate.now().plusDays(21),
            None
          )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationshipFails(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 500
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe PartialAuth
    }

    "return 201 when there is PartialAuth but clientId is MTDITID (as if ETMP create relationship call failed previously)" in {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdItSupp,
          mtdItId,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 404 when there is a PartialAuth existing for a Supporting Agent" should {}
  }

  "PUT /agent/alt-itsa/update/:arn" should {

    val request = FakeRequest("PUT", "/agent/alt-itsa/update/:arn").withHeaders("Authorization" -> "Bearer testtoken")

    "return 204 when there are no alt-itsa invitations found for the agent" in {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
    }

    "return 500 when DES MtdItId call fails" in {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 500
      await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)) shouldBe Some(altItsaPending)
    }

    "return 204 when a DES MtdItId call returns 404 (a client is not signed up to MtdItId)" in {

      val altItsaPending1 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsUnknownFor(nino2)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2)
    }

    "return 204 when MtdItId found and replace Nino on multiple records for the same client" in {

      val altItsa1 = Invitation(
        invitationId = InvitationId.create(arn.value, nino.value, Service.MtdIt.id)(Service.MtdIt.invitationIdPrefix),
        arn = arn,
        clientType = Some("personal"),
        service = Service.MtdIt,
        clientId = nino,
        suppliedClientId = nino,
        expiryDate = LocalDate.now,
        detailsForEmail = None,
        clientActionUrl = None,
        origin = None,
        events = List(StatusChangeEvent(LocalDateTime.now, Expired))
      )

      await(invitationsRepo.collection.insertOne(altItsa1).toFuture())

      val altItsa2 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsa1.invitationId)).get.clientId.underlying shouldBe mtdItId
      await(invitationsRepo.findByInvitationId(altItsa2.invitationId)).get.clientId.underlying shouldBe mtdItId
    }

    "return 204 when MtdItId found and replace Nino on multiple records for different clients" in {

      val altItsaPending1 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val anotherAgent = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino2, mtdItId2)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204
      await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)) shouldBe Some(altItsaPending1.copy(clientId = mtdItId))
      await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)) shouldBe Some(altItsaPending2.copy(clientId = mtdItId2))
      await(invitationsRepo.findByInvitationId(anotherAgent.invitationId)) shouldBe Some(anotherAgent)
    }

    "return 204 when MtdItId found and create relationship succeeds, updating status to Accepted" in {

      val altItsaPending1 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val anotherAgent = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino2,
          nino2,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      await(invitationsRepo.update(altItsaPending1, PartialAuth, LocalDateTime.now()))
      await(invitationsRepo.update(altItsaPending2, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino2, mtdItId2)

      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId2)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204

      val modified1 = await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId))
      val modified2 = await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId))
      val anotherArn = await(invitationsRepo.findByInvitationId(anotherAgent.invitationId))

      modified1.get.suppliedClientId shouldBe ClientIdentifier(nino)
      modified1.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified1.get.status shouldBe Accepted

      modified2.get.suppliedClientId shouldBe ClientIdentifier(nino2)
      modified2.get.clientId shouldBe ClientIdentifier(mtdItId2)
      modified2.get.status shouldBe Accepted

      anotherArn.get.status shouldBe Pending
      anotherArn.get.clientId shouldBe ClientIdentifier(nino2)
    }

    "return 500 when create relationship fails for a client" in {

      val altItsaPending1 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, LocalDateTime.now().truncatedTo(MILLIS), LocalDate.now().plusDays(21), None)
      )

      await(invitationsRepo.update(altItsaPending1, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)

      givenCreateRelationshipFails(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 500

      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val mongoResult = await(invitationsRepo.findInvitationsBy(arn = Some(arn)))

      mongoResult.count(_.status == PartialAuth) shouldBe 1
    }

    "return 204 when there is PartialAuth but clientId is MTDITID (as if ETMP create relationship call failed previously)" in {

      val altItsaPending1 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId2,
          nino2,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      val altItsaPending3 = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId3,
          nino3,
          None,
          LocalDateTime.now().truncatedTo(MILLIS),
          LocalDate.now().plusDays(21),
          None
        )
      )

      await(invitationsRepo.update(altItsaPending1, PartialAuth, LocalDateTime.now()))
      await(invitationsRepo.update(altItsaPending2, PartialAuth, LocalDateTime.now()))
      await(invitationsRepo.update(altItsaPending3, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenMtdItIdIsKnownFor(nino2, mtdItId2)
      givenMtdItIdIsKnownFor(nino3, mtdItId3)

      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId2)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId3)

      val result = await(controller.altItsaUpdateAgent(arn)(request))
      status(result) shouldBe 204

      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)
      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId2)
      verifyCreateRelationshipWasSent(arn, "HMRC-MTD-IT", "MTDITID", mtdItId3)

      val modified1 = await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId))
      val modified2 = await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId))
      val modified3 = await(invitationsRepo.findByInvitationId(altItsaPending3.invitationId))

      modified1.get.suppliedClientId shouldBe ClientIdentifier(nino)
      modified1.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified1.get.status shouldBe Accepted

      modified2.get.suppliedClientId shouldBe ClientIdentifier(nino2)
      modified2.get.clientId shouldBe ClientIdentifier(mtdItId2)
      modified2.get.status shouldBe Accepted

      modified3.get.suppliedClientId shouldBe ClientIdentifier(nino3)
      modified3.get.clientId shouldBe ClientIdentifier(mtdItId3)
      modified3.get.status shouldBe Accepted
    }

    "PUT /agencies/:arn/invitations/sent/:invitationId/cancel" should {

      val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel")
        .withHeaders("X-Session-ID" -> "1234")
        .withHeaders("Authorization" -> "Bearer testtoken")
      val getResult = FakeRequest("GET", "agencies/:arn/invitations/sent/:invitationId").withHeaders("Authorization" -> "Bearer testtoken")

      s"return 204 when a PartialAuth invitation is successfully cancelled" in {

        val pending: Invitation = await(createInvitation(arn, altItsaClient))
        val partialAuth: Invitation = await(invitationsRepo.update(pending, PartialAuth, LocalDateTime.now()))

        givenAuditConnector()
        givenAuthorisedAsAgent(arn)
        givenPlatformAnalyticsRequestSent(true)

        val response = controller.cancelInvitation(arn, partialAuth.invitationId)(request)
        status(response) shouldBe 204

        val updatedInvitation = controller.getSentInvitation(arn, partialAuth.invitationId)(getResult)
        status(updatedInvitation) shouldBe 200

        val invitationStatus = contentAsJson(updatedInvitation).as[TestHalResponseInvitation].status

        invitationStatus shouldBe DeAuthorised.toString
      }

      "return InvitationNotFound when there is no invitation to cancel" in {
        val request = FakeRequest("PUT", "agencies/:arn/invitations/sent/:invitationId/cancel").withHeaders("Authorization" -> "Bearer testtoken")
        givenAuditConnector()
        givenAuthorisedAsAgent(arn)

        val response = controller.cancelInvitation(arn, InvitationId("A7GJRTMY4DS3T"))(request)

        await(response) shouldBe InvitationNotFound
      }
    }
  }

  def createInvitation(arn: Arn, testClient: TestClient[_], hasEmail: Boolean = true): Future[Invitation] =
    invitationsRepo.create(
      arn,
      testClient.clientType,
      testClient.service,
      testClient.clientId,
      testClient.suppliedClientId,
      if (hasEmail) Some(dfe(testClient.clientName)) else None,
      Instant.now().atZone(ZoneOffset.UTC).toLocalDateTime,
      LocalDate.now().plusDays(21),
      None
    )
}
