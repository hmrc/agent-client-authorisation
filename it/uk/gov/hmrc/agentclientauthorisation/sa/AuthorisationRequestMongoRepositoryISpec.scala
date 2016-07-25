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
import uk.gov.hmrc.agentclientauthorisation.model.{Accepted, AgentClientAuthorisationRequest, Pending, StatusChangeEvent}
import uk.gov.hmrc.agentclientauthorisation.repository.AuthorisationRequestMongoRepository
import uk.gov.hmrc.agentclientauthorisation.support.ResetMongoBeforeTest
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AuthorisationRequestMongoRepositoryISpec extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with Eventually with Inside {

  private val now = DateTime.now()

  def repository = new AuthorisationRequestMongoRepository() {
    override def withCurrentTime[A](f: (DateTime) => A): A = f(now)
  }

  "AuthorisationRequestRepository create" should {

    "create a new StatusChangedEvent of Pending" in {

      val created: AgentClientAuthorisationRequest = await(repository.create(AgentCode("123"), SaUtr("123456789")))

      inside(created) { case event: AgentClientAuthorisationRequest =>
        event.agentCode shouldBe AgentCode("123")
          event.clientSaUtr shouldBe SaUtr("123456789")
          event.regime shouldBe "sa"
          event.events.loneElement shouldBe StatusChangeEvent(now, Pending)
      }
    }
  }

  "AuthorisationRequestRepository update" should {

    "create a new StatusChangedEvent" in {
      val created: AgentClientAuthorisationRequest = await(repository.create(AgentCode("123"), SaUtr("123456789")))

      inside(await(repository.update(created.id, Accepted))) {
        case AgentClientAuthorisationRequest(created.id, _, _, _, events) =>
          events shouldBe List(
            StatusChangeEvent(now, Pending),
            StatusChangeEvent(now, Accepted)
          )
      }
    }
  }

  "AuthorisationRequestRepository list" should {

    "return previously created and updated elements" in {
      await(Future.sequence(List(
        repository.create(AgentCode("123"), SaUtr("1234567890")),
        repository.create(AgentCode("123"), SaUtr("1234567891")).map(q => repository.update(q.id, Accepted))
      )))

      val list = await(repository.list(AgentCode("123")))

      inside(list(0)) {
        case AgentClientAuthorisationRequest(_, AgentCode("123"), SaUtr("1234567890"), "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
      inside(list(1)) {
        case AgentClientAuthorisationRequest(_, AgentCode("123"), SaUtr("1234567891"), "sa", List(StatusChangeEvent(date1, Pending), StatusChangeEvent(date2, Accepted))) =>
          date1 shouldBe now
          date2 shouldBe now
      }
    }

    "return elements only for the given agent code" in {
      await(Future.sequence(List(
        repository.create(AgentCode("123A"), SaUtr("1234567890")),
        repository.create(AgentCode("123B"), SaUtr("1234567891"))
      )))

      val list = await(repository.list(AgentCode("123B")))

      inside(list.loneElement) {
        case AgentClientAuthorisationRequest(_, AgentCode("123B"), SaUtr("1234567891"), "sa", List(StatusChangeEvent(date, Pending))) =>
          date shouldBe now
      }
    }
  }
}
