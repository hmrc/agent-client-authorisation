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
import uk.gov.hmrc.agentclientauthorisation.connectors.{AddressDetails, BusinessDetails, DesConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsRepository
import uk.gov.hmrc.agentclientauthorisation.support.TransitionInvitation
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.domain.{Generator, Nino}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsServiceSpec extends UnitSpec with MockitoSugar with BeforeAndAfterEach with TransitionInvitation {
  val invitationsRepository = mock[InvitationsRepository]
  val relationshipsConnector = mock[RelationshipsConnector]
  val desConnector = mock[DesConnector]

  val service = new InvitationsService(invitationsRepository, relationshipsConnector, desConnector)

  val arn = Arn("12345")
  val nino = new Generator().nextNino
  val ninoAsString = nino.value
  val mtdItId = MtdItId("0123456789")

  implicit val hc = HeaderCarrier()



  override protected def beforeEach(): Unit = {
    super.beforeEach()
    reset(invitationsRepository, relationshipsConnector)
  }

  "acceptInvitation" should {
    "create a relationship" when {
      "invitation status update succeeds" in {
        whenRelationshipIsCreated thenReturn(Future successful {})
        val acceptedTestInvitation = transitionInvitation(testInvitation, Accepted)
        whenStatusIsChangedTo(Accepted) thenReturn(Future successful acceptedTestInvitation)

        val response = await(service.acceptInvitation(testInvitation))

        response shouldBe Right(acceptedTestInvitation)
      }

      "invitation status update fails" in {
        pending
      }
    }

    "should not create a relationship" when {
      "invitation has already been accepted" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Accepted)))

        response shouldBe Left("The invitation cannot be transitioned to Accepted because its current status is Accepted. Only Pending invitations may be transitioned to Accepted.")
      }
      "invitation has been cancelled" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Cancelled)))

        response shouldBe Left("The invitation cannot be transitioned to Accepted because its current status is Cancelled. Only Pending invitations may be transitioned to Accepted.")
      }
      "invitation has been rejected" in {
        val response = await(service.acceptInvitation(testInvitationWithStatus(Rejected)))

        response shouldBe Left("The invitation cannot be transitioned to Accepted because its current status is Rejected. Only Pending invitations may be transitioned to Accepted.")
      }
    }

    "should not change the invitation status when relationship creation fails" in {
      whenRelationshipIsCreated thenReturn(Future failed ReactiveMongoException("Mongo error"))

      intercept[ReactiveMongoException] {
        await(service.acceptInvitation(testInvitation))
      }

      verify(invitationsRepository, never()).update(any[BSONObjectID], any[InvitationStatus])(any())
    }
  }


  "rejectInvitation" should {
    "update the invitation status" in {
      val rejectedTestInvitation = transitionInvitation(testInvitation, Rejected)
      whenStatusIsChangedTo(Rejected) thenReturn rejectedTestInvitation

      val response = await(service.rejectInvitation(testInvitation))

      response shouldBe Right(rejectedTestInvitation)
    }

    "not reject a cancelled invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe Left("The invitation cannot be transitioned to Rejected because its current status is Cancelled. Only Pending invitations may be transitioned to Rejected.")
    }

    "not reject an accepted invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe Left("The invitation cannot be transitioned to Rejected because its current status is Accepted. Only Pending invitations may be transitioned to Rejected.")
    }

    "not reject an already rejected invitation" in {
      val response = await(service.rejectInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe Left("The invitation cannot be transitioned to Rejected because its current status is Rejected. Only Pending invitations may be transitioned to Rejected.")
    }
  }

  "cancelInvitation" should {
    "update the invitation status" in {
      val cancelledTestInvitation = transitionInvitation(testInvitation, Cancelled)
      whenStatusIsChangedTo(Cancelled) thenReturn cancelledTestInvitation

      val response = await(service.cancelInvitation(testInvitation))

      response shouldBe Right(cancelledTestInvitation)
    }

    "not cancel a cancelled invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Cancelled)))

      response shouldBe Left("The invitation cannot be transitioned to Cancelled because its current status is Cancelled. Only Pending invitations may be transitioned to Cancelled.")
    }

    "not cancel an accepted invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Accepted)))

      response shouldBe Left("The invitation cannot be transitioned to Cancelled because its current status is Accepted. Only Pending invitations may be transitioned to Cancelled.")
    }

    "not cancel an already rejected invitation" in {
      val response = await(service.cancelInvitation(testInvitationWithStatus(Rejected)))

      response shouldBe Left("The invitation cannot be transitioned to Cancelled because its current status is Rejected. Only Pending invitations may be transitioned to Cancelled.")
    }
  }

  "findInvitation" should {
    "return None when the passed invitationId cannot be parsed as a BSONObjectId" in {
      await(service.findInvitation("not a BSON Object Id")) shouldBe None
    }
  }

  "translateToMtdItId" should {
    val mtdItId: String = "mtdItId01234"
    val clientIdTypeMtdItId: String = "MTDITID"

    "return the mtfItId if supplied" in {
      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(mtdItId,clientIdTypeMtdItId))
      shouldBeMtdItId.head.value shouldBe mtdItId
    }

    "return None if an invalid client id type is supplied" in {
      val shouldBeNone: Option[MtdItId] = await(service.translateToMtdItId("id","noSuchType"))
      shouldBeNone.isEmpty shouldBe true
    }

    "return an mtdItId if a nino is supplied for which there is a matching DES business partner record with an mtdItId" in {
      val nino = "WX772755B"

      whenDesBusinessPartnerRecordExistsFor(Nino(nino),mtdItId)

      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(nino,"ni"))
      shouldBeMtdItId.head.value shouldBe mtdItId
    }

    "return None if a nino is supplied for which there is a matching DES business partner record without a mtdItId" in {
      val nino = "WX772755B"

      whenDesBusinessPartnerRecordExistsWithoutMtdItIdFor(Nino(nino))

      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(nino,"ni"))
      shouldBeMtdItId shouldBe None
    }

    "return None if a nino is supplied for which there is no matching DES business partner record" in {
      val nino = "WX772755B"

      whenDesBusinessPartnerRecordDoesNotExist

      val shouldBeMtdItId: Option[MtdItId] = await(service.translateToMtdItId(nino,"ni"))
      shouldBeMtdItId shouldBe None
    }
  }

  "create" should {
    "create an invitation" in {
      val serviceId: String = "serviceId"
      val nino: String = "nino"
      val postcode: String = "postcode"

      service.create(arn, serviceId, mtdItId, postcode, nino, "ni")
      verify(invitationsRepository, times(1)).create(arn, serviceId, mtdItId, postcode, nino, "ni")
    }
  }

  private def testInvitationWithStatus(status: InvitationStatus) = Invitation(generate,
    arn,
    "mtd-sa",
    mtdItId.value,
    "A11 1AA",
    "nino",
    "ni",
    List(StatusChangeEvent(now(), Pending), StatusChangeEvent(now(), status))
  )

  private def testInvitation = Invitation(generate,
    arn,
    "mtd-sa",
    mtdItId.value,
    "A11 1AA",
    "nino",
    "ni",
    List(StatusChangeEvent(now(), Pending))
  )

  private def whenStatusIsChangedTo(status: InvitationStatus): OngoingStubbing[Future[Invitation]] = {
    when(invitationsRepository.update(any[BSONObjectID], eqs(status))(any()))
  }

  private def whenRelationshipIsCreated: OngoingStubbing[Future[Unit]] = {
    when(relationshipsConnector.createRelationship(arn, mtdItId))
  }

  private def whenDesBusinessPartnerRecordExistsFor(nino: Nino, mtdItId: String): OngoingStubbing[Future[Option[BusinessDetails]]] = {
    when(desConnector.getBusinessDetails(nino)).thenReturn(
      Future successful Some(BusinessDetails(AddressDetails("postcode", None),Some(MtdItId(mtdItId)))))
  }

  private def whenDesBusinessPartnerRecordExistsWithoutMtdItIdFor(nino: Nino): OngoingStubbing[Future[Option[BusinessDetails]]] = {
    when(desConnector.getBusinessDetails(nino)).thenReturn(
      Future successful Some(BusinessDetails(AddressDetails("postcode", None), None)))
  }

  private def whenDesBusinessPartnerRecordDoesNotExist: OngoingStubbing[Future[Option[BusinessDetails]]] = {
    when(desConnector.getBusinessDetails(nino)).thenReturn( Future successful None)
  }
}
