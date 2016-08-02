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
import uk.gov.hmrc.domain.AgentCode
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
      val clientRegimeId = "1A"

      inside(addRequest((agentCode, "sa", clientRegimeId, "user-details"))) {
        case event: AgentClientAuthorisationRequest =>
          event.agentCode shouldBe agentCode
          event.clientRegimeId shouldBe clientRegimeId
          event.agentUserDetailsLink shouldBe "user-details"
          event.regime shouldBe "sa"
          event.events.loneElement shouldBe StatusChangeEvent(now, Pending)
      }
    }
  }

  "AuthorisationRequestRepository update" should {

    "create a new StatusChangedEvent" in {

      val created = addRequest((AgentCode("2"), "sa", "2A", "user-details"))
      val updated = update(created.id, Accepted)

      inside(updated) {
        case AgentClientAuthorisationRequest(created.id, _, _, _, _, events) =>
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
      val saUtr1 = "SAUTR3A"
      val vrn2 = "VRN3B"

      val requests = addRequests((agentCode, "sa", saUtr1, "user-details-1"), (agentCode, "vat", vrn2, "user-details-2"))
      update(requests.last.id, Accepted)

      val list = listByAgentCode(agentCode).sortBy(_.clientRegimeId)

      inside(list head) {
        case AgentClientAuthorisationRequest(_, `agentCode`, "sa", `saUtr1`, "user-details-1", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(list(1)) {
        case AgentClientAuthorisationRequest(_, `agentCode`, "vat", `vrn2`, "user-details-2", List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now
      }
    }

    "return elements only for the given agent code" in {

      addRequests((AgentCode("123A"), "sa", "1234567890", "user-details"), (AgentCode("123B"), "sa", "1234567891", "user-details"))

      inside( listByAgentCode(AgentCode("123B")) loneElement ) {
        case AgentClientAuthorisationRequest(_, AgentCode("123B"), "sa", "1234567891", "user-details", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }
  }


  "AuthorisationRequestRepository listing by clientSaUtr" should {

    "return an empty list when there is no such SaUtr" in {

      listByClientRegimeId("no-such-id") shouldBe Nil
    }

    "return a single agent request" in {

      val regimeId = "4A"
      val agentCode = AgentCode("4")
      val userDetailsLink = "user-details"

      addRequest((agentCode, "sa", regimeId, userDetailsLink))

      inside(listByClientRegimeId(regimeId) loneElement) {
        case AgentClientAuthorisationRequest(_, `agentCode`, "sa", `regimeId`, `userDetailsLink`, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }

    "return a multiple requests for the same client" in {

      val firstAgent = AgentCode("5")
      val secondAgent = AgentCode("6")
      val clientSaUtr = "5A"
      val userDetailsLink = "user-details"


      addRequests(
        (firstAgent, "sa", clientSaUtr, userDetailsLink),
        (secondAgent, "sa", clientSaUtr, userDetailsLink),
        (AgentCode("should-not-show-up"), "sa", "another-client", userDetailsLink)
      )

      val requests = listByClientRegimeId(clientSaUtr).sortBy(_.agentCode.value)

      requests.size shouldBe 2

      inside(requests head) {
        case AgentClientAuthorisationRequest(_, `firstAgent`, "sa", `clientSaUtr`, `userDetailsLink`, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }

      inside(requests(1)) {
        case AgentClientAuthorisationRequest(_, `secondAgent`, "sa", `clientSaUtr`, `userDetailsLink`, List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }
  }


  private type Request = (AgentCode, String, String, String)

  private def addRequests(requests: Request*) = {
    await(Future sequence requests.map { case (code: AgentCode, regime: String, clientRegimeId: String, userDetailsLink: String) => repository.create(code, regime, clientRegimeId, userDetailsLink) })
  }

  private def addRequest(requests: Request) = addRequests(requests) head

  private def listByAgentCode(agentCode: AgentCode) = await(repository.list(agentCode))

  private def listByClientRegimeId(regimeId: String) = await(repository.list(regimeId))

  private def update(id:BSONObjectID, status:AuthorisationStatus) = await(repository.update(id, status))
}
