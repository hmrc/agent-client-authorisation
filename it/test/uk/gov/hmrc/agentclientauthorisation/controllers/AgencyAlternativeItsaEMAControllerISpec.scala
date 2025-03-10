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

import org.scalatest.Assertion
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepositoryImpl
import uk.gov.hmrc.agentclientauthorisation.support.PlatformAnalyticsStubs
import uk.gov.hmrc.agentmtdidentifiers.model.{ClientIdentifier, Service}

import java.time.LocalDateTime

class AgencyAlternativeItsaEMAControllerISpec extends BaseISpec with PlatformAnalyticsStubs {

  lazy val invitationsRepo = app.injector.instanceOf(classOf[InvitationsRepositoryImpl])

  lazy val controller: AgencyInvitationsController = app.injector.instanceOf[AgencyInvitationsController]

  override def beforeEach(): Unit = {
    super.beforeEach()
    await(invitationsRepo.ensureIndexes())
    ()
  }

  trait TestSetup {
    givenAuthorisedEmptyPredicate
  }

  def compareInvitationsIgnoringTimestamps(result: Invitation, expected: Invitation): Assertion = {
    result.events.size shouldBe expected.events.size
    result.events.zip(expected.events).foreach { case (x, y) =>
      x.status shouldBe y.status
    }
    result.copy(events = List.empty) shouldBe expected.copy(events = List.empty)
  }

  "PUT /alt-itsa/HMRC-MTD-IT/update/:nino" should {

    val request = FakeRequest("PUT", "/alt-itsa/update/:nino").withHeaders("Authorization" -> "Bearer testtoken")

    "return 404 when there are no alt-itsa invitations found for the client" in new TestSetup {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
          None
        )
      )

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 404
    }

    "return 500 when DES MtdItId call fails leaving alt-itsa invitation untouched" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, None)
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 500
      compareInvitationsIgnoringTimestamps(await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)).get, altItsaPending)
    }

    "return 404 when DES MtdItId call return 404 (client not signed up to MtdItId) leaving alt-itsa invitation untouched" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, None)
      )

      givenMtdItIdIsUnknownFor(nino)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 404
      compareInvitationsIgnoringTimestamps(await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)).get, altItsaPending)
    }

    "return 204 when client has MtdItId, updating the invitation store to replace Nino" in new TestSetup {

      val altItsaPending1 = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, None)
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdIt,
          nino,
          nino,
          None,
          None
        )
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 204
      compareInvitationsIgnoringTimestamps(
        await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)).get,
        altItsaPending1.copy(clientId = mtdItId)
      )
      compareInvitationsIgnoringTimestamps(
        await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)).get,
        altItsaPending2.copy(clientId = mtdItId)
      )
    }

    "return 201 (Created) when there is PartialAuth invitation " in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, None)
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

    "return 500 when there is PartialAuth invitation but create relationship call fails (Nino will be replaced)" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(arn, Some("personal"), Service.MtdIt, nino, nino, None, None)
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

    "return 201 when there is PartialAuth but clientId is MTDITID (as if create relationship call previously failed)" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdIt,
          mtdItId,
          nino,
          None,
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

    "return 404 when there is only a PartialAuth existing for a Supporting Agent" in new TestSetup {
      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            None
          )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT")(request))
      status(result) shouldBe 404
    }
  }

  "PUT /alt-itsa/HMRC-MTD-IT-SUPP/update/:nino" should {

    val request = FakeRequest("PUT", "/alt-itsa/HMRC-MTD-IT-SUPP/update/:nino").withHeaders("Authorization" -> "Bearer testtoken")

    "return 404 when there are no alternative itsa invitations found for the client" in new TestSetup {

      await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdItSupp,
          mtdItId,
          nino,
          None,
          None
        )
      )

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 404
    }

    "return 500 when DES MtdItId call fails leaving alt-itsa invitation untouched" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            None
          )
      )

      givenDesReturnsServiceUnavailable()

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 500
      compareInvitationsIgnoringTimestamps(await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)).get, altItsaPending)
    }

    "return 404 when DES MtdItId call return 404 (client not signed up to MtdItId) leaving alt-itsa invitation untouched" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            None
          )
      )

      givenMtdItIdIsUnknownFor(nino)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 404
      compareInvitationsIgnoringTimestamps(await(invitationsRepo.findByInvitationId(altItsaPending.invitationId)).get, altItsaPending)
    }

    "return 204 when client has MtdItIdSupp, updating the invitation store to replace Nino" in new TestSetup {

      val altItsaPending1 = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            None
          )
      )

      val altItsaPending2 = await(
        invitationsRepo.create(
          arn2,
          Some("personal"),
          Service.MtdItSupp,
          nino,
          nino,
          None,
          None
        )
      )

      givenMtdItIdIsKnownFor(nino, mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 204
      compareInvitationsIgnoringTimestamps(
        await(invitationsRepo.findByInvitationId(altItsaPending1.invitationId)).get,
        altItsaPending1.copy(clientId = mtdItId)
      )
      compareInvitationsIgnoringTimestamps(
        await(invitationsRepo.findByInvitationId(altItsaPending2.invitationId)).get,
        altItsaPending2.copy(clientId = mtdItId)
      )
    }

    "return 201 (Created) when there is PartialAuth invitation and ETMP create relationship call succeeds" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
            None
          )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT-SUPP", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 500 when there is PartialAuth invitation but create relationship call fails (Nino will be replaced)" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdItSupp,
            nino,
            nino,
            None,
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

    "return 201 when there is PartialAuth but clientId is MTDITID (as if create relationship call failed previously)" in new TestSetup {

      val altItsaPending = await(
        invitationsRepo.create(
          arn,
          Some("personal"),
          Service.MtdItSupp,
          mtdItId,
          nino,
          None,
          None
        )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT-SUPP", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 201
      val modified = await(invitationsRepo.findByInvitationId(altItsaPending.invitationId))

      modified.get.clientId shouldBe ClientIdentifier(mtdItId)
      modified.get.status shouldBe Accepted
    }

    "return 404 when there is only a PartialAuth existing for a Main Agent" in new TestSetup {
      val altItsaPending = await(
        invitationsRepo
          .create(
            arn,
            Some("personal"),
            Service.MtdIt,
            nino,
            nino,
            None,
            None
          )
      )
      await(invitationsRepo.update(altItsaPending, PartialAuth, LocalDateTime.now()))

      givenMtdItIdIsKnownFor(nino, mtdItId)
      givenCreateRelationship(arn, "HMRC-MTD-IT", "MTDITID", mtdItId)

      val result = await(controller.altItsaUpdateEMA(nino, "HMRC-MTD-IT-SUPP")(request))
      status(result) shouldBe 404
    }
  }
}
