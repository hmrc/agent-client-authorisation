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

package uk.gov.hmrc.agentclientauthorisation.service

import org.joda.time.DateTime.now
import org.mockito.Matchers.{eq => eqs, _}
import org.mockito.Mockito._
import org.mockito.stubbing.OngoingStubbing
import org.scalatest.BeforeAndAfterEach
import org.scalatest.mock.MockitoSugar
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONObjectID.generate
import reactivemongo.core.errors.ReactiveMongoException
import uk.gov.hmrc.agentclientauthorisation.connectors.RelationshipsConnector
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class InvitationsServiceTest extends UnitSpec with MockitoSugar with BeforeAndAfterEach {
  val invitationsRepository = mock[InvitationsRepository]
  val relationshipsConnector = mock[RelationshipsConnector]

  val service = new InvitationsService(invitationsRepository, relationshipsConnector)

  val arn = Arn("12345")
  val mtdClientId = MtdClientId("67890")

  implicit val hc = HeaderCarrier()



  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(invitationsRepository, relationshipsConnector)
  }

  "acceptInvitation" should {
    "create a relationship" when {
      "invitation status update succeeds" in {
        whenRelationshipIsCreated thenReturn(Future successful {})
        whenStatusIsChangedTo(Accepted) thenReturn(Future successful invitation)

        val response = await(service.acceptInvitation(invitation))

        response shouldBe true
      }

      "invitation status update fails" in {
        pending
      }
    }

    "should not create a relationship" when {
      "invitation has already been accepted" in {
        val response = await(service.acceptInvitation(invitationWithStatus(Accepted)))

        response shouldBe false
      }
      "invitation has been cancelled" in {
        val response = await(service.acceptInvitation(invitationWithStatus(Cancelled)))

        response shouldBe false
      }
      "invitation has been rejected" in {
        val response = await(service.acceptInvitation(invitationWithStatus(Rejected)))

        response shouldBe false
      }
    }

    "should not change the invitation status when relationship creation fails" in {
      whenRelationshipIsCreated thenReturn(Future failed ReactiveMongoException("Mongo error"))

      intercept[ReactiveMongoException] {
        await(service.acceptInvitation(invitation))
      }

      verify(invitationsRepository, never()).update(any[BSONObjectID], any[InvitationStatus])
    }
  }


  "rejectInvitation" should {
    "update the invitation status" in {
      whenStatusIsChangedTo(Rejected) thenReturn invitation

      val response = await(service.rejectInvitation(invitation))

      response shouldBe true
    }

    "not reject a cancelled invitation" in {
      val response = await(service.rejectInvitation(invitationWithStatus(Cancelled)))

      response shouldBe false
    }

    "not reject an accepted invitation" in {
      val response = await(service.rejectInvitation(invitationWithStatus(Accepted)))

      response shouldBe false
    }

    "not reject an already rejected invitation" in {
      val response = await(service.rejectInvitation(invitationWithStatus(Rejected)))

      response shouldBe false
    }
  }

  private def invitationWithStatus(status: InvitationStatus) = Invitation(generate,
    arn,
    "mtd-sa",
    mtdClientId.value,
    "A11 1AA",
    List(StatusChangeEvent(now(), Pending), StatusChangeEvent(now(), status))
  )

  private def invitation = Invitation(generate,
    arn,
    "mtd-sa",
    mtdClientId.value,
    "A11 1AA",
    List(StatusChangeEvent(now(), Pending))
  )

  private def whenStatusIsChangedTo(status: InvitationStatus): OngoingStubbing[Future[Invitation]] = {
    when(invitationsRepository.update(any[BSONObjectID], eqs(status)))
  }

  private def whenRelationshipIsCreated: OngoingStubbing[Future[Unit]] = {
    when(relationshipsConnector.createRelationship(arn, mtdClientId))
  }
}
