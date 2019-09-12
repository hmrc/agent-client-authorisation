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
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONObjectID.parse
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, ResetMongoBeforeTest}
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, InvitationId, MtdItId, Vrn}
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsMongoRepositoryISpec
    extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside with MockitoSugar
    with MongoApp {

  override implicit lazy val mongoConnectorForTest: MongoConnector =
    MongoConnector(mongoUri, Some(MongoApp.failoverStrategyForTest))

  private val now = DateTime.now()
  val date = LocalDate.now()
  val invitationITSA = Invitation(
    BSONObjectID.generate(),
    InvitationId("ABERULMHCKKW3"),
    Arn(arn),
    Some("personal"),
    Service.MtdIt,
    mtdItId1,
    nino1,
    date,
    None,
    None,
    List.empty)
  val invitationIRV = Invitation(
    BSONObjectID.generate(),
    InvitationId("B9SCS2T4NZBAX"),
    Arn(arn),
    Some("personal"),
    Service.PersonalIncomeRecord,
    nino1,
    nino1,
    date,
    None,
    None,
    List.empty)
  val invitationVAT = Invitation(
    BSONObjectID.generate(),
    InvitationId("CZTW1KY6RTAAT"),
    Arn(arn),
    Some("business"),
    Service.Vat,
    vrn,
    vrn,
    date,
    None,
    None,
    List.empty)

  implicit lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(mongoConfiguration)
      .configure(
        "invitation-status-update-scheduler.enabled" -> false,
        "mongodb-migration.enabled"                  -> false
      )

  lazy val mockReactiveMongoComponent: ReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  lazy val repository: InvitationsRepositoryImpl = new InvitationsRepositoryImpl(mockReactiveMongoComponent) {
    override def withCurrentTime[A](f: DateTime => A): A = f(now)
  }

  override def beforeEach() {
    super.beforeEach()
    await(repository.ensureIndexes)
  }

  val ninoValue = nino1.value

  "create" should {
    "create a new StatusChangedEvent of Pending" in inside(addInvitation(now, invitationITSA)) {
      case Invitation(_, _, arnValue, _, service, clientId, _, _, _, _, events) =>
        arnValue shouldBe Arn(arn)
        clientId shouldBe ClientIdentifier(mtdItId1)
        service shouldBe Service.MtdIt
        events.loneElement.status shouldBe Pending
    }

    "create a new StatusChangedEvent which can be found using a reconstructed id" in {

      val invitation =
        await(addInvitation(now, invitationITSA))

      val id: BSONObjectID = invitation.id
      val stringifieldId: String = id.stringify
      val reconstructedId: BSONObjectID = parse(stringifieldId).get
      val foundInvitation: model.Invitation = await(repository.findById(reconstructedId)).head

      id shouldBe foundInvitation.id
    }
  }

  "update" should {
    "create a new StatusChangedEvent" in {

      val created = addInvitation(now, invitationITSA)
      val updated = update(created, Accepted, now)

      inside(updated) {
        case Invitation(created.id, _, _, _, _, _, _, _,_, _, events) =>
          events shouldBe List(StatusChangeEvent(now, Pending), StatusChangeEvent(now, Accepted))
      }
    }
  }

  "listing by arn" should {

    "return previously created and updated elements" in {

      val requests = addInvitations(now, invitationITSA, invitationIRV)

      update(requests.last, Accepted, now)

      val list = listByArn(Arn(arn)).sortBy(_.clientId.value)

      inside(list.head) {
        case Invitation(
            _,
            _,
            Arn(`arn`),
            _,
            Service.PersonalIncomeRecord,
            _,
            _,
            _,
            _,
            _,
            List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now

      }

      inside(list(1)) {
        case Invitation(_, _, Arn(`arn`), _, Service.MtdIt, _, _, _, _, _, List(StatusChangeEvent(date1, Pending))) =>
          date1 shouldBe now
      }
    }

    "return elements only for the given agent code" in {

      val arn2 = "ABCDEF123457"
      val invitationITSA2 = invitationITSA.copy(arn = Arn(arn2))

      addInvitations(now, invitationITSA, invitationITSA2)

      inside(listByArn(Arn(arn)).loneElement) {
        case Invitation(
            _,
            _,
            Arn(`arn`),
            _,
            Service.MtdIt,
            ClientIdentifier(`mtdItId1`),
            _,
            _,
            _,
            _,
            List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }
    }

    "return elements for the specified service only" in {
      val clientId1 = MtdItId("MTD-REG-3A")

      val invitationITSA1 = invitationITSA.copy(clientId = clientId1)

      addInvitations(now, invitationITSA1, invitationIRV)

      val list1 = listByArn(Arn(arn), Seq(Service.MtdIt), None, None, None)

      list1.size shouldBe 1
      list1.head.clientId.underlying shouldBe clientId1

      val list2 = listIdAndExpiryByArn(Arn(arn), Some(Service.MtdIt), None, None, None)
      list2.size shouldBe 1

      list2.map(_.invitationId) shouldBe list1.map(_.invitationId)
    }

    "return elements for the specified client only" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      val invitationITSA1 = invitationITSA.copy(clientId = clientId1)
      val invitationITSA2 = invitationITSA.copy(clientId = clientId2)

      addInvitations(now, invitationITSA1, invitationITSA2)

      val list = listByArn(Arn(arn), Seq.empty[Service], Some(clientId1.value), None, None)

      list.size shouldBe 1
      list.head.clientId.underlying shouldBe clientId1
    }

    "return elements with the specified status" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      val invitationITSA1 = invitationITSA.copy(clientId = clientId1)
      val invitationITSA2 = invitationITSA.copy(clientId = clientId2)

      val invitations = addInvitations(now, invitationITSA1, invitationITSA2)

      update(invitations.head, Accepted, DateTime.now())

      val list = listByArn(Arn(arn), Seq.empty[Service], None, Some(Pending), None)

      list.size shouldBe 1
      list.head.clientId.underlying shouldBe clientId2
    }

    "not return elements created before the specified date" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      val invitationITSA1 = invitationITSA.copy(clientId = clientId1)
      val invitationITSA2 = invitationITSA.copy(clientId = clientId2)
      val invitationITSA3 = invitationITSA.copy(clientId = clientId1)

      val invitations =
        addInvitations(now.minusDays(30), invitationITSA1) ++
          addInvitations(now.minusDays(20), invitationITSA2) ++
          addInvitations(now.minusDays(10), invitationITSA3)

      update(invitations.head, Expired, DateTime.now())
      update(invitations(1), Rejected, DateTime.now().minusDays(11))
      update(invitations(2), Accepted, DateTime.now().minusDays(2))

      val list1 = listByArn(Arn(arn), Seq.empty[Service], None, None, Some(now.minusDays(10).toLocalDate))
      list1.size shouldBe 1
      list1.head.clientId.underlying shouldBe clientId1
      list1.head.status shouldBe Accepted

      val list2 = listByArn(Arn(arn), Seq.empty[Service], None, None, Some(now.minusDays(20).toLocalDate))
      list2.size shouldBe 2
      list2.head.clientId.underlying shouldBe clientId1
      list2.head.status shouldBe Accepted
      list2(1).clientId.underlying shouldBe clientId2
      list2(1).status shouldBe Rejected

      val list21 = listByArn(Arn(arn), Seq.empty[Service], None, None, Some(now.minusDays(21).toLocalDate))
      list21.size shouldBe 2
      list21.head.clientId.underlying shouldBe clientId1
      list21.head.status shouldBe Accepted
      list21(1).clientId.underlying shouldBe clientId2
      list21(1).status shouldBe Rejected

      val list3 = listByArn(Arn(arn), Seq.empty[Service], None, None, Some(now.minusDays(30).toLocalDate))
      list3.size shouldBe 3
      list3.head.clientId.underlying shouldBe clientId1
      list3.head.status shouldBe Accepted
      list3(1).clientId.underlying shouldBe clientId2
      list3(1).status shouldBe Rejected
      list3(2).clientId.underlying shouldBe clientId1
      list3(2).status shouldBe Expired

      val list0 = listByArn(Arn(arn), Seq.empty[Service], None, None, Some(now.minusDays(9).toLocalDate))
      list0.size shouldBe 0

      val list01 = listByArn(Arn(arn), Seq.empty[Service], None, None, Some(now.toLocalDate))
      list01.size shouldBe 0
    }
  }

  "listing by clientId" should {

    "return an empty list when there is no such service-clientId pair" in {
      val service = Service.MtdIt

      addInvitation(now, invitationITSA)

      listByClientId(service, MtdItId("no-such-id")) shouldBe Nil
      listByClientId(Service.PersonalIncomeRecord, mtdItId1) shouldBe Nil
    }

    "return a single agent request" in {

      val service = Service.MtdIt

      addInvitation(now, invitationITSA)

      inside(listByClientId(service, mtdItId1).loneElement) {
        case Invitation(
            _,
            _,
            Arn(`arn`),
            _,
            `service`,
            mtdItId,
            _,
            _,
            _,
            _,
            List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }
    }

    "return a multiple requests for the same client" in {

      val firstAgent = Arn("5")
      val secondAgent = Arn("6")
      val thirdAgent = Arn("should-not-show-up")
      val service = Service.MtdIt

      val invitationITSAFirstAgent = invitationITSA.copy(arn = firstAgent)
      val invitationITSASecondAgent = invitationITSA.copy(arn = secondAgent)
      val invitationITSAThirdAgent = invitationITSA.copy(arn = thirdAgent, clientId = MtdItId("another-client"))

      addInvitations(now, invitationITSAFirstAgent, invitationITSASecondAgent, invitationITSAThirdAgent)

      val requests = listByClientId(service, mtdItId1).sortBy(_.arn.value)

      requests.size shouldBe 2

      inside(requests.head) {
        case Invitation(_, _, `firstAgent`, _, `service`, _, _, _, _, _, List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }

      inside(requests(1)) {
        case Invitation(_, _, `secondAgent`, _, `service`, _, _, _, _, _, List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }
    }

    "return elements with the specified status" in {

      val invitations = addInvitations(now, invitationITSA, invitationITSA)

      update(invitations.head, Accepted, DateTime.now())

      val list = listByClientId(Service.MtdIt, mtdItId1, Some(Pending))

      list.size shouldBe 1
      list.head.status shouldBe Pending
    }

    "findInvitationsInfoBy" in {

      val invitations: Seq[Invitation] = for (i <- 1 to 10)
        yield
          Invitation.createNew(
            Arn(arn),
            Some("personal"),
            Service.MtdIt,
            MtdItId(s"AB${i}23456B"),
            MtdItId(s"AB${i}23456A"),
            None,
            now,
            now.plusDays(14).toLocalDate)

      await(Future.sequence(invitations.map(repository.insert)))

      val result1: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("MTDITID" -> "AB523456A"), Some(Pending)))
      result1 shouldBe Seq(
        invitations
          .map(invitation => InvitationInfo(invitation.invitationId, invitation.expiryDate, Pending))
          .apply(4))

      val result2: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("MTDITID" -> "AB523456B"), Some(Pending)))
      result2 shouldBe Seq(
        invitations
          .map(invitation => InvitationInfo(invitation.invitationId, invitation.expiryDate, Pending))
          .apply(4))
    }

    "find pending invitations, reject them and then find them again" in {

      val itsaInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.MtdIt,
        MtdItId("ABCD123456C"),
        MtdItId("ABCD123456C"),
        None,
        now,
        now.plusDays(14).toLocalDate)

      val pirInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.PersonalIncomeRecord,
        Nino("AB123456B"),
        Nino("AB123456A"),
        None,
        now,
        now.plusDays(14).toLocalDate)

      val vatInvitation = Invitation
        .createNew(
          Arn(arn),
          Some("personal"),
          Service.Vat,
          Vrn("442820662"),
          Vrn("442820662"),
          None,
          now,
          now.plusDays(14).toLocalDate)

      await(repository.insert(itsaInvitation))
      await(repository.insert(pirInvitation))
      await(repository.insert(vatInvitation))

      val result1: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("MTDITID" -> "ABCD123456C"), Some(Pending)))
      result1 shouldBe Seq(InvitationInfo(itsaInvitation.invitationId, itsaInvitation.expiryDate, Pending))

      val result2: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("NINO" -> "AB123456B"), Some(Pending)))
      result2 shouldBe Seq(InvitationInfo(pirInvitation.invitationId, pirInvitation.expiryDate, Pending))

      val result3: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("VRN" -> "442820662"), Some(Pending)))
      result3 shouldBe Seq(InvitationInfo(vatInvitation.invitationId, vatInvitation.expiryDate, Pending))

      await(repository.update(itsaInvitation, Rejected, DateTime.now()))
      await(repository.update(pirInvitation, Rejected, DateTime.now()))
      await(repository.update(vatInvitation, Rejected, DateTime.now()))

      val result1Rejected: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("MTDITID" -> "ABCD123456C"), Some(Rejected)))
      result1Rejected shouldBe Seq(InvitationInfo(itsaInvitation.invitationId, itsaInvitation.expiryDate, Rejected))

      val result2Rejected: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("NINO" -> "AB123456B"), Some(Rejected)))
      result2Rejected shouldBe Seq(InvitationInfo(pirInvitation.invitationId, pirInvitation.expiryDate, Rejected))

      val result3Rejected: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq("VRN" -> "442820662"), Some(Rejected)))
      result3Rejected shouldBe Seq(InvitationInfo(vatInvitation.invitationId, vatInvitation.expiryDate, Rejected))
    }
  }

  "refreshAllInvitations" should {
    "refresh all invitations" in {

      val itsaInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.MtdIt,
        MtdItId("ABCD123456C"),
        MtdItId("ABCD123456C"),
        None,
        now,
        now.plusDays(14).toLocalDate)

      val pirInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.PersonalIncomeRecord,
        Nino("AB123456B"),
        Nino("AB123456A"),
        None,
        now,
        now.plusDays(14).toLocalDate)

      val vatInvitation = Invitation
        .createNew(
          Arn(arn),
          Some("personal"),
          Service.Vat,
          Vrn("442820662"),
          Vrn("442820662"),
          None,
          now,
          now.plusDays(14).toLocalDate)

      await(repository.insert(itsaInvitation))
      await(repository.insert(pirInvitation))
      await(repository.insert(vatInvitation))

      await(repository.refreshAllInvitations)
    }
  }

  "removeEmailDetails" should {
    "remove DetailsForEmail from invitation" in {

      val dfe = DetailsForEmail("abc@def.com", "Mr Agent", "Mr Client")

      val itsaInvitation: Invitation = Invitation.createNew(
      Arn(arn),
      Some("personal"),
      Service.MtdIt,
      MtdItId("ABCD123456C"),
      MtdItId("ABCD123456C"),
      Some(dfe),
      now,
      now.plusDays(14).toLocalDate)

      await(repository.insert(itsaInvitation))

      val storedInvitation: Invitation = await(repository.findByInvitationId(itsaInvitation.invitationId)).get

      storedInvitation.detailsForEmail shouldBe Some(dfe)

      await(repository.removeEmailDetails(storedInvitation))

      val updatedInvitation: Invitation = await(repository.findByInvitationId(itsaInvitation.invitationId)).get

      updatedInvitation.detailsForEmail shouldBe None
    }
  }

  private def addInvitations(startDate: DateTime, invitations: Invitation*) =
    await(Future sequence invitations.map {
      case Invitation(_, _, arnValue, clientType, service, clientId, suppliedClientId, _, _, _, _) =>
        repository.create(
          arnValue,
          clientType,
          service,
          clientId,
          suppliedClientId,
          None,
          startDate,
          startDate.plusDays(20).toLocalDate)
    })

  private def addInvitation(startDate: DateTime, invitations: Invitation) = addInvitations(startDate, invitations).head

  private def listByArn(arn: Arn) = await(repository.findInvitationsBy(Some(arn), Seq.empty[Service], None, None, None))

  private def listByArn(
    arn: Arn,
    service: Seq[Service],
    clientId: Option[String],
    status: Option[InvitationStatus],
    createdOnOrAfter: Option[LocalDate]): Seq[Invitation] =
    await(repository.findInvitationsBy(Some(arn), service, clientId, status, createdOnOrAfter))

  private def listIdAndExpiryByArn(
    arn: Arn,
    service: Option[Service],
    clientId: Option[String],
    status: Option[InvitationStatus],
    createdOnOrAfter: Option[LocalDate]): Seq[InvitationInfo] =
    await(repository.findInvitationInfoBy(Some(arn), service, clientId, status, createdOnOrAfter))

  private def listByClientId(service: Service, clientId: MtdItId, status: Option[InvitationStatus] = None) =
    await(repository.findInvitationsBy(services = Seq(service), clientId = Some(clientId.value), status = status))

  private def update(invitation: Invitation, status: InvitationStatus, updateDate: DateTime) =
    await(repository.update(invitation, status, updateDate))

  private def removeEmailDetails(invitation: Invitation) = await(repository.removeEmailDetails(invitation))

}
