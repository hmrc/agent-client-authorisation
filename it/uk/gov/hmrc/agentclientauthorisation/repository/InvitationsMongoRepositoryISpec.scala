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

package uk.gov.hmrc.agentclientauthorisation.repository

import org.joda.time.{DateTime, LocalDate}
import org.scalatest.Inside
import org.scalatest.LoneElement._
import org.scalatest.concurrent.Eventually
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONObjectID.parse
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model.ClientIdentifier.ClientId
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, ResetMongoBeforeTest}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import play.modules.reactivemongo.ReactiveMongoComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsMongoRepositoryISpec
    extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside with MockitoSugar
    with MongoApp {

  private val now = DateTime.now()

  implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(mongoConfiguration)

  val mockReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  private def repository = new InvitationsRepository(mockReactiveMongoComponent) {
    override def withCurrentTime[A](f: DateTime => A): A = f(now)
  }

  val ninoValue = nino1.value

  "create" should {

    "create a new StatusChangedEvent of Pending" in inside(
      addInvitation((Arn(arn), Service.MtdIt, mtdItId1, nino1.value, "ni", now))) {
      case event =>
        event.arn shouldBe Arn(arn)
        event.clientId shouldBe ClientIdentifier(mtdItId1)
        event.service shouldBe Service.MtdIt
        event.events.loneElement.status shouldBe Pending
    }

    "create a new StatusChangedEvent which can be found using a reconstructed id" in {

      val invitation: model.Invitation =
        await(addInvitation((Arn(arn), Service.MtdIt, mtdItId1, nino1.value, "ni", now)))

      val id: BSONObjectID = invitation.id
      val stringifiedId: String = id.stringify
      val reconstructedId: BSONObjectID = parse(stringifiedId).get
      val foundInvitation: model.Invitation = await(repository.findById(reconstructedId)).head

      id shouldBe foundInvitation.id
    }
  }

  "update" should {

    "create a new StatusChangedEvent" in {

      val created = addInvitation((Arn(arn), Service.MtdIt, mtdItId1, nino1.value, "ni", now))
      val updated = update(created.id, Accepted, now)

      inside(updated) {
        case Invitation(created.id, _, _, _, _, _, _, events) =>
          events shouldBe List(StatusChangeEvent(now, Pending), StatusChangeEvent(now, Accepted))
      }
    }
  }

  "listing by arn" should {

    "return previously created and updated elements" in {

      val saUtr1 = "SAUTR3A"
      val vrn2 = "VRN3B"

      val requests = addInvitations(
        (Arn(arn), Service.MtdIt, MtdItId(saUtr1), ninoValue, "ni", now),
        (Arn(arn), Service.PersonalIncomeRecord, MtdItId(vrn2), ninoValue, "ni", now)
      )
      update(requests.last.id, Accepted, now)

      val list = listByArn(Arn(arn)).sortBy(_.clientId.value)

      inside(list.head) {
        case Invitation(_, _, Arn(`arn`), Service.MtdIt, _, _, _, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(list(1)) {
        case Invitation(
            _,
            _,
            Arn(`arn`),
            Service.PersonalIncomeRecord,
            _,
            _,
            _,
            List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now
      }
    }

    "return elements only for the given agent code" in {

      val arn2 = "ABCDEF123457"

      addInvitations(
        (Arn(arn), Service.MtdIt, mtdItId1, nino1.value, "ni", now),
        (Arn(arn2), Service.MtdIt, mtdItId1, nino1.value, "ni", now))

      inside(listByArn(Arn(arn)).loneElement) {
        case Invitation(
            _,
            _,
            Arn(`arn`),
            Service.MtdIt,
            ClientIdentifier(`mtdItId1`),
            _,
            _,
            List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return elements for the specified service only" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      addInvitations(
        (Arn(arn), Service.MtdIt, clientId1, nino1.value, "ni", now),
        (Arn(arn), Service.PersonalIncomeRecord, clientId2, nino1.value, "ni", now)
      )

      val list = listByArn(Arn(arn), Some(Service.MtdIt), None, None, None)

      list.size shouldBe 1
      list.head.clientId.underlying shouldBe clientId1
    }

    "return elements for the specified client only" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      addInvitations(
        (Arn(arn), Service.MtdIt, clientId1, nino1.value, "ni", now),
        (Arn(arn), Service.MtdIt, clientId2, nino1.value, "ni", now))

      val list = listByArn(Arn(arn), None, Some(clientId1.value), None, None)

      list.size shouldBe 1
      list.head.clientId.underlying shouldBe clientId1
    }

    "return elements with the specified status" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      val invitations = addInvitations(
        (Arn(arn), Service.MtdIt, clientId1, nino1.value, "ni", now),
        (Arn(arn), Service.MtdIt, clientId2, nino1.value, "ni", now))
      update(invitations.head.id, Accepted, DateTime.now())

      val list = listByArn(Arn(arn), None, None, Some(Pending), None)

      list.size shouldBe 1
      list.head.clientId.underlying shouldBe clientId2
    }

    "not return elements created before the specified date" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      val invitations = addInvitations(
        (Arn(arn), Service.MtdIt, clientId1, nino1.value, "ni", now.minusDays(30)),
        (Arn(arn), Service.MtdIt, clientId2, nino1.value, "ni", now.minusDays(20)),
        (Arn(arn), Service.MtdIt, clientId1, nino1.value, "ni", now.minusDays(10))
      )

      update(invitations.head.id, Expired, DateTime.now())
      update(invitations(1).id, Rejected, DateTime.now().minusDays(11))
      update(invitations(2).id, Accepted, DateTime.now().minusDays(2))

      val list1 = listByArn(Arn(arn), None, None, None, Some(now.minusDays(10).toLocalDate))
      list1.size shouldBe 1
      list1.head.clientId.underlying shouldBe clientId1
      list1.head.status shouldBe Accepted

      val list2 = listByArn(Arn(arn), None, None, None, Some(now.minusDays(20).toLocalDate))
      list2.size shouldBe 2
      list2.head.clientId.underlying shouldBe clientId1
      list2.head.status shouldBe Accepted
      list2(1).clientId.underlying shouldBe clientId2
      list2(1).status shouldBe Rejected

      val list21 = listByArn(Arn(arn), None, None, None, Some(now.minusDays(21).toLocalDate))
      list21.size shouldBe 2
      list21.head.clientId.underlying shouldBe clientId1
      list21.head.status shouldBe Accepted
      list21(1).clientId.underlying shouldBe clientId2
      list21(1).status shouldBe Rejected

      val list3 = listByArn(Arn(arn), None, None, None, Some(now.minusDays(30).toLocalDate))
      list3.size shouldBe 3
      list3.head.clientId.underlying shouldBe clientId1
      list3.head.status shouldBe Accepted
      list3(1).clientId.underlying shouldBe clientId2
      list3(1).status shouldBe Rejected
      list3(2).clientId.underlying shouldBe clientId1
      list3(2).status shouldBe Expired

      val list0 = listByArn(Arn(arn), None, None, None, Some(now.minusDays(9).toLocalDate))
      list0.size shouldBe 0

      val list01 = listByArn(Arn(arn), None, None, None, Some(now.toLocalDate))
      list01.size shouldBe 0
    }
  }

  "listing by clientId" should {

    "return an empty list when there is no such service-clientId pair" in {
      val service = Service.MtdIt
      val postcode = "postcode"

      addInvitation((Arn(arn), service, mtdItId1, nino1.value, "ni", now))

      listByClientId(service, MtdItId("no-such-id")) shouldBe Nil
      listByClientId(Service.PersonalIncomeRecord, mtdItId1) shouldBe Nil
    }

    "return a single agent request" in {

      val service = Service.MtdIt
      val postcode = "postcode"

      addInvitation((Arn(arn), service, mtdItId1, nino1.value, "ni", now))

      inside(listByClientId(service, mtdItId1).loneElement) {
        case Invitation(_, _, Arn(`arn`), `service`, mtdItId1, _, _, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return a multiple requests for the same client" in {

      val firstAgent = Arn("5")
      val secondAgent = Arn("6")
      val service = Service.MtdIt

      addInvitations(
        (firstAgent, service, mtdItId1, nino1.value, "ni", now),
        (secondAgent, service, mtdItId1, nino1.value, "ni", now),
        (Arn("should-not-show-up"), Service.MtdIt, MtdItId("another-client"), nino1.value, "ni", now)
      )

      val requests = listByClientId(service, mtdItId1).sortBy(_.arn.value)

      requests.size shouldBe 2

      inside(requests.head) {
        case Invitation(_, _, `firstAgent`, `service`, mtdItId1, _, _, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(requests(1)) {
        case Invitation(_, _, `secondAgent`, `service`, mtdItId1, _, _, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return elements with the specified status" in {

      val invitations =
        addInvitations(
          (Arn(arn), Service.MtdIt, mtdItId1, nino1.value, "ni", now),
          (Arn(arn), Service.MtdIt, mtdItId1, nino1.value, "ni", now))
      update(invitations.head.id, Accepted, DateTime.now())

      val list = listByClientId(Service.MtdIt, mtdItId1, Some(Pending))

      list.size shouldBe 1
      list.head.status shouldBe Pending
    }
  }

  private type Invitation = (Arn, Service, MtdItId, String, String, DateTime)

  private def addInvitations(invitations: Invitation*) =
    await(Future sequence invitations.map {
      case (
          code: Arn,
          service: Service,
          clientId: MtdItId,
          suppliedClientIdValue: String,
          suppliedClientIdType: String,
          startDate: DateTime) =>
        val suppliedClientId: ClientId = ClientIdentifier(suppliedClientIdValue, suppliedClientIdType)
        repository.create(
          code,
          service,
          ClientIdentifier(clientId),
          suppliedClientId,
          startDate,
          startDate.plusDays(20).toLocalDate)
    })

  private def addInvitation(invitations: Invitation) = addInvitations(invitations) head

  private def listByArn(arn: Arn) = await(repository.list(arn, None, None, None, None))

  private def listByArn(
    arn: Arn,
    service: Option[Service],
    clientId: Option[String],
    status: Option[InvitationStatus],
    createdOnOrAfter: Option[LocalDate]) =
    await(repository.list(arn, service, clientId, status, createdOnOrAfter))

  private def listByClientId(service: Service, clientId: MtdItId, status: Option[InvitationStatus] = None) =
    await(repository.list(service, clientId, status))

  private def update(id: BSONObjectID, status: InvitationStatus, updateDate: DateTime) =
    await(repository.update(id, status, updateDate))

}
