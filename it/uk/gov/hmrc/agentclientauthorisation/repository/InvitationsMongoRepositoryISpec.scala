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

import com.kenshoo.play.metrics.Metrics
import org.bson.types.ObjectId
import org.mongodb.scala.model.Filters
import org.scalatest.Inside
import org.scalatest.LoneElement._
import org.scalatest.concurrent.Eventually
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{AppAndStubs, UnitSpec}
import uk.gov.hmrc.agentmtdidentifiers.model._
import uk.gov.hmrc.domain.Nino
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import java.time.temporal.ChronoUnit.MILLIS
import java.time.{LocalDate, LocalDateTime}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsMongoRepositoryISpec
    extends UnitSpec  with Eventually with Inside with AppAndStubs
     with DefaultPlayMongoRepositorySupport[Invitation] {

  val metrics = app.injector.instanceOf[Metrics]
  override val repository  = new InvitationsRepositoryImpl(mongoComponent, metrics)

  val date = LocalDate.now()
  val invitationITSA = Invitation(
    ObjectId.get(),
    InvitationId("ABERULMHCKKW3"),
    Arn(arn),
    Some("personal"),
    Service.MtdIt,
    mtdItId1,
    nino1,
    date,
    None,
    false,
    None,
    None,
    None,
    List.empty
  )
  val invitationIRV = Invitation(
    ObjectId.get(),
    InvitationId("B9SCS2T4NZBAX"),
    Arn(arn),
    Some("personal"),
    Service.PersonalIncomeRecord,
    nino1,
    nino1,
    date,
    None,
    false,
    None,
    None,
    None,
    List.empty
  )

  val invitationURN = Invitation(
    ObjectId.get(),
    InvitationId("EPN7M763YPGLJ"),
    Arn(arn),
    Some("personal"),
    Service.TrustNT,
    urn,
    urn,
    date,
    None,
    false,
    None,
    None,
    None,
    List.empty
  )

  val invitationVAT = Invitation(
    ObjectId.get(),
    InvitationId("CZTW1KY6RTAAT"),
    Arn(arn),
    Some("business"),
    Service.Vat,
    vrn,
    vrn,
    date,
    None,
    false,
    None,
    None,
    None,
    List.empty)
  val ninoValue = nino1.value
  private val now = LocalDateTime.now().truncatedTo(MILLIS)


  "create" should {
    "create a new StatusChangedEvent of Pending" in inside(addInvitation(now, invitationITSA)) {
      case Invitation(_, _, arnValue, _, service, clientId, _, _, _, _, _, _, _, events) =>
        arnValue shouldBe Arn(arn)
        clientId shouldBe ClientIdentifier(mtdItId1)
        service shouldBe Service.MtdIt
        events.loneElement.status shouldBe Pending
    }

    "create a new StatusChangedEvent which can be found using a reconstructed id" in {

      val invitation = await(repository.create(invitationITSA.arn, clientType = invitationITSA.clientType, service = invitationITSA.service,
        clientId = invitationITSA.clientId,
        suppliedClientId = invitationITSA.suppliedClientId, None,
        startDate = now,
        expiryDate = LocalDate.now(),None))

      val id: ObjectId = invitation._id
      val stringifieldId: String = id.toString
      val reconstructedId: ObjectId = new ObjectId(stringifieldId)
      val foundInvitation: model.Invitation = await(repository.collection.find(Filters.equal("_id", reconstructedId)).head())

      id shouldBe foundInvitation._id
      foundInvitation.isRelationshipEnded shouldBe false
    }
  }

  "update" should {
    "create a new StatusChangedEvent" in {

      val created = addInvitation(now, invitationITSA)
      val updated = update(created, Accepted, now)

      inside(updated) {
        case Invitation(created._id, _, _, _, _, _, _, _, _, _, _, _, _, events) =>
          events shouldBe List(StatusChangeEvent(now, Pending), StatusChangeEvent(now, Accepted))
      }
    }
  }

  "setRelationshipEnded" should {
    "set isRelationshipEnded flag to be true" in {

      val created = addInvitation(now, invitationITSA)
      val ended = setRelationshipEnded(created)

      inside(ended) {
        case Invitation(created._id, _, _, _, _, _, _, _, _, isRelationshipEnded, _, _, _, _) =>
          isRelationshipEnded shouldBe true
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
            _,
            _,
            _,
            List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now

      }

      inside(list(1)) {
        case Invitation(_, _, Arn(`arn`), _, Service.MtdIt, _, _, _, _, _, _, _, _, List(StatusChangeEvent(date1, Pending))) =>
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
      val invitationITSA2 = invitationITSA.copy(_id = ObjectId.get(), clientId = clientId2)

      val invitations = addInvitations(now, invitationITSA1, invitationITSA2)

      update(invitations.head, Accepted, LocalDateTime.now())

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

      update(invitations.head, Expired, LocalDateTime.now())
      update(invitations(1), Rejected, LocalDateTime.now().minusDays(11))
      update(invitations(2), Accepted, LocalDateTime.now().minusDays(2))

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

  "listing latest by clientId" should {

    "return an empty list when there is no invitation present" in {

      getLatestByClientId(urn) shouldBe None

    }

    "return latest invitation when there are multiple invitations present" in {

      val invitation = invitationURN.copy(events = List(StatusChangeEvent(now, Pending)))

      addInvitation(now, invitation)

      inside(getLatestByClientId(urn).get) {
        case Invitation(_, _, Arn(`arn`), _, _, _, _, _, _, _, _, _, _, List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }

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
        case Invitation(_, _, Arn(`arn`), _, `service`, mtdItId, _, _, _, _, _, _, _, List(StatusChangeEvent(dateValue, Pending))) =>
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
        case Invitation(_, _, `firstAgent`, _, `service`, _, _, _, _, _, _, _, _, List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }

      inside(requests(1)) {
        case Invitation(_, _, `secondAgent`, _, `service`, _, _, _, _, _, _, _, _, List(StatusChangeEvent(dateValue, Pending))) =>
          dateValue shouldBe now
      }
    }

    "return elements with the specified status" in {

      val invitations = addInvitations(now,  invitationIRV, invitationITSA)

      update(invitations.head, Accepted, LocalDateTime.now())

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
            now.plusDays(21).toLocalDate,
            None)

      await(Future.sequence(invitations.map(invit => repository.collection.insertOne(invit).toFuture())))

      val result1: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-IT", "MTDITID", "AB523456A")), Some(Pending)))
      result1 shouldBe Seq(
        invitations
          .map(invitation =>
            InvitationInfo(
              invitation.invitationId,
              invitation.expiryDate,
              Pending,
              Arn(arn),
              Service.MtdIt,
              invitation.isRelationshipEnded,
              invitation.relationshipEndedBy,
              invitation.events
          ))
          .apply(4))

      val result2: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-IT", "MTDITID", "AB523456B")), Some(Pending)))
      result2 shouldBe Seq(
        invitations
          .map(invitation =>
            InvitationInfo(
              invitation.invitationId,
              invitation.expiryDate,
              Pending,
              Arn(arn),
              Service.MtdIt,
              invitation.isRelationshipEnded,
              invitation.relationshipEndedBy,
              invitation.events
          ))
          .apply(4))

      val result3: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-IT", "MTDITID", "AB623456B")), None))
      result3 shouldBe Seq(
        invitations
          .map(
            inv =>
              InvitationInfo(
                inv.invitationId,
                inv.expiryDate,
                inv.status,
                Arn(arn),
                Service.MtdIt,
                inv.isRelationshipEnded,
                inv.relationshipEndedBy,
                inv.events))
          .apply(5))
    }

    "find pending invitations, reject them and then find them again" in {

      val itsaInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.MtdIt,
        MtdItId("ABCD123456C"),
        nino,
        None,
        now,
        now.plusDays(21).toLocalDate,
        None)

      val pirInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.PersonalIncomeRecord,
        Nino("AB123456B"),
        Nino("AB123456A"),
        None,
        now,
        now.plusDays(21).toLocalDate,
        None)

      val vatInvitation = Invitation
        .createNew(Arn(arn), Some("personal"), Service.Vat, Vrn("442820662"), Vrn("442820662"), None,
          now, now.plusDays(21).toLocalDate, None)

      await(repository.collection.insertOne(itsaInvitation).toFuture())
      await(repository.collection.insertOne(pirInvitation).toFuture())
      await(repository.collection.insertOne(vatInvitation).toFuture())

      val result1: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-IT", "MTDITID", "ABCD123456C")), Some(Pending)))
      result1 shouldBe Seq(
        InvitationInfo(
          itsaInvitation.invitationId,
          itsaInvitation.expiryDate,
          Pending,
          Arn(arn),
          Service.MtdIt,
          false,
          None,
          List(StatusChangeEvent(now, Pending))))

      val result2: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("PERSONAL-INCOME-RECORD", "NINO", "AB123456B")), Some(Pending)))
      result2 shouldBe Seq(
        InvitationInfo(
          pirInvitation.invitationId,
          pirInvitation.expiryDate,
          Pending,
          Arn(arn),
          Service.PersonalIncomeRecord,
          false,
          None,
          List(StatusChangeEvent(now, Pending))))

      val result3: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-VAT", "VRN", "442820662")), Some(Pending)))
      result3 shouldBe Seq(
        InvitationInfo(
          vatInvitation.invitationId,
          vatInvitation.expiryDate,
          Pending,
          Arn(arn),
          Service.Vat,
          false,
          None,
          List(StatusChangeEvent(now, Pending))))

      val updateTime = LocalDateTime.now().truncatedTo(MILLIS)
      await(repository.update(itsaInvitation, Rejected, updateTime))
      await(repository.update(pirInvitation, Rejected, updateTime))
      await(repository.update(vatInvitation, Rejected, updateTime))

      val result1Rejected: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-IT", "MTDITID", "ABCD123456C")), Some(Rejected)))
      result1Rejected shouldBe Seq(
        InvitationInfo(
          itsaInvitation.invitationId,
          itsaInvitation.expiryDate,
          Rejected,
          Arn(arn),
          Service.MtdIt,
          false,
          None,
          List(StatusChangeEvent(now, Pending), StatusChangeEvent(updateTime, Rejected))
        ))

      val result2Rejected: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("PERSONAL-INCOME-RECORD", "NINO", "AB123456B")), Some(Rejected)))
      result2Rejected shouldBe Seq(
        InvitationInfo(
          pirInvitation.invitationId,
          pirInvitation.expiryDate,
          Rejected,
          Arn(arn),
          Service.PersonalIncomeRecord,
          false,
          None,
          List(StatusChangeEvent(now, Pending), StatusChangeEvent(updateTime, Rejected))
        ))

      val result3Rejected: Seq[InvitationInfo] =
        await(repository.findInvitationInfoBy(Arn(arn), Seq(("HMRC-MTD-VAT", "VRN", "442820662")), Some(Rejected)))
      result3Rejected shouldBe Seq(
        InvitationInfo(
          vatInvitation.invitationId,
          vatInvitation.expiryDate,
          Rejected,
          Arn(arn),
          Service.Vat,
          false,
          None,
          List(StatusChangeEvent(now, Pending), StatusChangeEvent(updateTime, Rejected))
        ))
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
        now.plusDays(21).toLocalDate,
        None)

      val pirInvitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.PersonalIncomeRecord,
        Nino("AB123456B"),
        Nino("AB123456A"),
        None,
        now,
        now.plusDays(21).toLocalDate,
        None)

      val vatInvitation = Invitation
        .createNew(Arn(arn), Some("personal"), Service.Vat, Vrn("442820662"), Vrn("442820662"), None, now, now.plusDays(21).toLocalDate, None)

      await(repository.collection.insertOne(itsaInvitation).toFuture())
      await(repository.collection.insertOne(pirInvitation).toFuture())
      await(repository.collection.insertOne(vatInvitation).toFuture())
    }
  }

  "removeEmailDetails" should {

    val dfe = DetailsForEmail("abc@def.com", "Mr Agent", "Mr Client")

    "keep detailsForEmail from invitation if invitation is younger than the given date" in {
      val itsaInvitation: Invitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.MtdIt,
        MtdItId("ABCD111111C"),
        MtdItId("ABCD111111C"),
        Some(dfe),
        now.minusDays(1),
        now.plusDays(21).toLocalDate,
        None)
      await(repository.collection.insertOne(itsaInvitation).toFuture())
      val storedInvitation: Invitation = await(repository.findByInvitationId(itsaInvitation.invitationId)).get
      storedInvitation.detailsForEmail shouldBe Some(dfe)

      await(repository.removePersonalDetails(now.minusDays(2)))
      val updatedInvitation: Invitation = await(repository.findByInvitationId(itsaInvitation.invitationId)).get
      updatedInvitation.detailsForEmail shouldBe Some(dfe)
    }

    "remove DetailsForEmail from invitation if invitation is older than the given date" in {
      val itsaInvitation: Invitation = Invitation.createNew(
        Arn(arn),
        Some("personal"),
        Service.MtdIt,
        MtdItId("ABCD222222C"),
        MtdItId("ABCD222222C"),
        Some(dfe),
        now.minusDays(1),
        now.plusDays(21).toLocalDate,
        None)
      await(repository.collection.insertOne(itsaInvitation).toFuture())
      val storedInvitation: Invitation = await(repository.findByInvitationId(itsaInvitation.invitationId)).get
      storedInvitation.detailsForEmail shouldBe Some(dfe)
      await(repository.removePersonalDetails(now))
      eventually {
        val updatedInvitation: Invitation = await(repository.findByInvitationId(itsaInvitation.invitationId)).get
        updatedInvitation.detailsForEmail shouldBe None
      }
    }
  }

  private def addInvitation(startDate: LocalDateTime, invitations: Invitation) = addInvitations(startDate, invitations).head

  private def addInvitations(startDate: LocalDateTime, invitations: Invitation*) =
    await(Future sequence invitations.map {
      case Invitation(_, _, arnValue, clientType, service, clientId, suppliedClientId, _, _, _, _, _, _, _) =>
        repository.create(arnValue, clientType, service, clientId, suppliedClientId, None, startDate, startDate.plusDays(20).toLocalDate, None)
    })

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

  private def update(invitation: Invitation, status: InvitationStatus, updateDate: LocalDateTime) =
    await(repository.update(invitation, status, updateDate))

  private def setRelationshipEnded(invitation: Invitation): Invitation =
    await(repository.setRelationshipEnded(invitation, "Agent"))

  private def getLatestByClientId(clientId: Urn) =
    await(repository.findLatestInvitationByClientId(clientId.value))
}
