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

package uk.gov.hmrc.agentclientauthorisation.sa

import org.joda.time.DateTime
import org.scalatest.Inside
import org.scalatest.LoneElement._
import org.scalatest.concurrent.Eventually
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model._
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestMongoRepository
import uk.gov.hmrc.agentclientauthorisation.support.ResetMongoBeforeTest
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorisationRequestMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside {

  private val now = DateTime.now()

  private def repository = new AuthorisationRequestMongoRepository() {
    override def withCurrentTime[A](f: (DateTime) => A): A = f(now)
  }


  "AuthorisationRequestRepository create" should {

    "create a new StatusChangedEvent of Pending" in {

      val agentCode = AgentCode("1")
      val saUtr = SaUtr("1A")

      inside(addRequest(agentCode -> saUtr)) {
        case event: AgentClientAuthorisationRequest =>
          event.agentCode shouldBe agentCode
          event.clientSaUtr shouldBe saUtr
          event.regime shouldBe "sa"
          event.events.loneElement shouldBe StatusChangeEvent(now, Pending)
      }
    }
  }

  "AuthorisationRequestRepository update" should {

    "create a new StatusChangedEvent" in {

      val created = addRequest(AgentCode("2"), SaUtr("2A"))
      val updated = update(created.id, Accepted)

      inside(updated) {
        case AgentClientAuthorisationRequest(created.id, _, _, _, events) =>
          events shouldBe List(
            StatusChangeEvent(now, Pending),
            StatusChangeEvent(now, Accepted)
          )
      }
    }
  }

  "AuthorisationRequestRepository listing by agentCode" should {

    "return previously created and updated elements" in {

      val agentCode = AgentCode("3")
      val saUtr1 = SaUtr("3A")
      val saUtr2 = SaUtr("3B")

      val requests = addRequests(agentCode -> saUtr1, agentCode -> saUtr2)
      update(requests.last.id, Accepted)

      val list = listByAgentCode(agentCode).sortBy(_.clientSaUtr.value)

      inside(list head) {
        case AgentClientAuthorisationRequest(_, `agentCode`, `saUtr1`, "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(list(1)) {
        case AgentClientAuthorisationRequest(_, `agentCode`, `saUtr2`, "sa", List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now
      }
    }

    "return elements only for the given agent code" in {

      addRequests(AgentCode("123A") -> SaUtr("1234567890"), AgentCode("123B") -> SaUtr("1234567891"))

      inside( listByAgentCode(AgentCode("123B")) loneElement ) {
        case AgentClientAuthorisationRequest(_, AgentCode("123B"), SaUtr("1234567891"), "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }
  }


  "AuthorisationRequestRepository listing by clientSaUtr" should {

    "return an empty list when there is no such SaUtr" in {

      listByClientSaUtr(SaUtr("no-such-id")) shouldBe Nil
    }

    "return a single agent request" in {

      val saUtr = SaUtr("4A")
      val agentCode = AgentCode("4")

      addRequest(agentCode, saUtr)

      inside(listByClientSaUtr(saUtr) loneElement) {
        case AgentClientAuthorisationRequest(_, `agentCode`, `saUtr`, "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return a multiple requests for the same client" in {

      val firstAgent = AgentCode("5")
      val secondAgent = AgentCode("6")
      val clientSaUtr = SaUtr("5A")

      addRequests(
        firstAgent -> clientSaUtr,
        secondAgent -> clientSaUtr,
        AgentCode("should-not-show-up") -> SaUtr("another-client")
      )

      val requests = listByClientSaUtr(clientSaUtr).sortBy(_.agentCode.value)

      requests.size shouldBe 2

      inside(requests head) {
        case AgentClientAuthorisationRequest(_, `firstAgent`, `clientSaUtr`, "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(requests(1)) {
        case AgentClientAuthorisationRequest(_, `secondAgent`, `clientSaUtr`, "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }
  }


  private type Request = (AgentCode, SaUtr)

  private def addRequests(requests: Request*) = {
    await(Future sequence requests.map { case (code: AgentCode, utr: SaUtr) => repository.create(code, utr) })
  }

  private def addRequest(requests: Request) = addRequests(requests) head

  private def listByAgentCode(agentCode: AgentCode) = await(repository.list(agentCode))

  private def listByClientSaUtr(saUtr: SaUtr) = await(repository.list(saUtr))

  private def update(id:BSONObjectID, status:AuthorisationStatus) = await(repository.update(id, status))
}
