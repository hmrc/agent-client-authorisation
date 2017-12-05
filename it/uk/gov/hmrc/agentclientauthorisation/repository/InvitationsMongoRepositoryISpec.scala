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

package uk.gov.hmrc.agentclientauthorisation.repository

import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.LoneElement._
import org.scalatest.concurrent.Eventually
import reactivemongo.bson.BSONObjectID
import reactivemongo.bson.BSONObjectID.parse
import uk.gov.hmrc.agentclientauthorisation.model
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.support.ResetMongoBeforeTest
import uk.gov.hmrc.agentmtdidentifiers.model.{Arn, MtdItId}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class InvitationsMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside {

  private val now = DateTime.now()

  private def repository = new InvitationsRepository(mongo()) {
    override def withCurrentTime[A](f: (DateTime) => A): A = f(now)
  }


  val ninoValue = nino1.value

  "create" should {

    "create a new StatusChangedEvent of Pending" in {

      inside(addInvitation((Arn(arn), "sa", mtdItId1, nino1.value, "ni", "postcode"))) {
        case event =>
          event.arn shouldBe Arn(arn)
          event.clientId shouldBe mtdItId1.value
          event.service shouldBe "sa"
          event.events.loneElement shouldBe StatusChangeEvent(now, Pending)
      }
    }

    "create a new StatusChangedEvent which can be found using a reconstructed id" in {

      val invitation: model.Invitation = await(addInvitation((Arn(arn), "sa", mtdItId1, nino1.value, "ni", "postcode")))

      val id: BSONObjectID = invitation.id
      val stringifiedId: String = id.stringify
      val reconstructedId: BSONObjectID = parse(stringifiedId).get
      val foundInvitation: model.Invitation = await(repository.findById(reconstructedId)).head

      id shouldBe foundInvitation.id
    }
  }

  "update" should {

    "create a new StatusChangedEvent" in {

      val created = addInvitation((Arn(arn), "sa", mtdItId1, nino1.value, "ni", "postcode"))
      val updated = update(created.id, Accepted)

      inside(updated) {
        case Invitation(created.id, _, _, _, _, _,_,_, events) =>
          events shouldBe List(
            StatusChangeEvent(now, Pending),
            StatusChangeEvent(now, Accepted)
          )
      }
    }
  }

  "listing by arn" should {

    "return previously created and updated elements" in {

      val saUtr1 = "SAUTR3A"
      val vrn2 = "VRN3B"

      val requests = addInvitations(
        (Arn(arn), "sa", MtdItId(saUtr1), ninoValue, "ni", "postcode-1"), (Arn(arn), "vat", MtdItId(vrn2), ninoValue, "ni", "postcode-2"))
      update(requests.last.id, Accepted)

      val list = listByArn(Arn(arn)).sortBy(_.clientId)

      inside(list head) {
        case Invitation(_, _, Arn(`arn`), "sa", `saUtr1`, "postcode-1", `ninoValue`, "ni", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(list(1)) {
        case Invitation(_, _, Arn(`arn`), "vat", `vrn2`, "postcode-2", `ninoValue`, "ni", List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now
      }
    }

    "return elements only for the given agent code" in {

      val arn2 = "ABCDEF123457"

      addInvitations(
        (Arn(arn), "sa", mtdItId1, nino1.value, "ni", "postcode"), (Arn(arn2), "sa", mtdItId1, nino1.value, "ni", "postcode"))

      inside(listByArn(Arn(arn)) loneElement) {
        case Invitation(_, _, Arn(`arn`), "sa", `mtdItId1`.value, "postcode", `ninoValue`, "ni", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return elements for the specified service only" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      addInvitations((Arn(arn), "mtd-sa", clientId1, nino1.value, "ni", "postcode-1"), (Arn(arn), "some-other-service", clientId2, nino1.value, "ni", "postcode-2"))

      val list = listByArn(Arn(arn), Some("mtd-sa"), None, None)

      list.size shouldBe 1
      list.head.clientId shouldBe clientId1.value
    }

    "return elements for the specified client only" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      addInvitations((Arn(arn), "mtd-sa", clientId1, nino1.value, "ni", "postcode-1"), (Arn(arn), "mtd-sa", clientId2, nino1.value, "ni", "postcode-2"))

      val list = listByArn(Arn(arn), None, Some(clientId1.value), None)

      list.size shouldBe 1
      list.head.clientId shouldBe clientId1.value
    }

    "return elements with the specified status" in {
      val clientId1 = MtdItId("MTD-REG-3A")
      val clientId2 = MtdItId("MTD-REG-3B")

      val invitations = addInvitations((Arn(arn), "mtd-sa", clientId1, nino1.value, "ni", "postcode-1"), (Arn(arn), "mtd-sa", clientId2, nino1.value, "ni", "postcode-2"))
      update(invitations.head.id, Accepted)

      val list = listByArn(Arn(arn), None, None, Some(Pending))

      list.size shouldBe 1
      list.head.clientId shouldBe clientId2.value
    }
  }


  "listing by clientId" should {

    "return an empty list when there is no such service-clientId pair" in {
      val service = "sa"
      val postcode = "postcode"

      addInvitation((Arn(arn), service, mtdItId1, nino1.value, "ni", postcode))

      listByClientId(service, MtdItId("no-such-id")) shouldBe Nil
      listByClientId("different-service", mtdItId1) shouldBe Nil
    }

    "return a single agent request" in {

      val service = "sa"
      val postcode = "postcode"

      addInvitation((Arn(arn), service, mtdItId1, nino1.value, "ni", postcode))

      inside(listByClientId(service, mtdItId1) loneElement) {
        case Invitation(_, _, Arn(`arn`), `service`, mtdItId1.value, `postcode`, `ninoValue`, "ni", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return a multiple requests for the same client" in {

      val firstAgent = Arn("5")
      val secondAgent = Arn("6")
      val service = "sa"
      val postcode = "postcode"


      addInvitations(
        (firstAgent, service, mtdItId1, nino1.value, "ni", postcode),
        (secondAgent, service, mtdItId1, nino1.value, "ni", postcode),
        (Arn("should-not-show-up"), "sa",  MtdItId("another-client"), nino1.value, "ni", postcode)
      )

      val requests = listByClientId(service, mtdItId1).sortBy(_.arn.value)

      requests.size shouldBe 2

      inside(requests head) {
        case Invitation(_, _, `firstAgent`, `service`, mtdItId1.value, `postcode`, `ninoValue`, "ni", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(requests(1)) {
        case Invitation(_, _, `secondAgent`, `service`, mtdItId1.value, `postcode`, `ninoValue`, "ni", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return elements with the specified status" in {

      val invitations =
        addInvitations(
          (Arn(arn), "mtd-sa", mtdItId1, nino1.value, "ni", "postcode-1"), (Arn(arn), "mtd-sa", mtdItId1, nino1.value, "ni", "postcode-1"))
      update(invitations.head.id, Accepted)

      val list = listByClientId("mtd-sa", mtdItId1, Some(Pending))

      list.size shouldBe 1
      list.head.status shouldBe Pending
    }
  }


  private type Invitation = (Arn, String, MtdItId, String, String, String)

  private def addInvitations(invitations: Invitation*) = {
    await(Future sequence invitations.map {
      case (code: Arn, service: String, clientId: MtdItId, suppliedClientId: String, suppliedClientIdType: String, postcode: String) =>
        repository.create(code, service, clientId, postcode, suppliedClientId, suppliedClientIdType)
    })
  }

  private def addInvitation(invitations: Invitation) = addInvitations(invitations) head

  private def listByArn(arn: Arn) = await(repository.list(arn, None, None, None))

  private def listByArn(arn: Arn, service: Option[String], clientId: Option[String], status: Option[InvitationStatus]) =
                            await(repository.list(arn, service, clientId, status))

  private def listByClientId(service: String, clientId: MtdItId, status: Option[InvitationStatus] = None) =
    await(repository.list(service, clientId, status))

  private def update(id: BSONObjectID, status: InvitationStatus) = await(repository.update(id, status))

}
