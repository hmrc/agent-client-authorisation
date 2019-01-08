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

import org.joda.time.DateTime
import org.scalatest.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.agentclientauthorisation.support.TestConstants._
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, ResetMongoBeforeTest}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAgentReferenceRepositoryISpec
    extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with MockitoSugar with MongoApp {

  private val now = DateTime.now()

  lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(mongoConfiguration)
      .configure(
        "invitation-status-update-scheduler.enabled" -> false
      )

  lazy val mockReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  lazy val repository = new MongoAgentReferenceRepository(mockReactiveMongoComponent)

  "AgentReferenceRepository" when {
    val multiInvitationRecord =
      AgentReferenceRecord("uid", Arn(arn), Seq("stan-lee"))

    "create" should {
      "successfully create a record in the repository" in {
        await(repository.create(multiInvitationRecord)) shouldBe 1
      }
    }

    "findBy" should {
      "successfully find a created record by its uid" in {
        await(repository.create(multiInvitationRecord))

        await(repository.findBy("uid")) shouldBe Some(multiInvitationRecord)
      }
    }

    "findByArn" should {
      "successfully find a created record by its arn" in {
        await(repository.create(multiInvitationRecord))

        await(repository.findByArn(Arn(arn))) shouldBe Some(multiInvitationRecord)
      }
    }

    "updateAgentName" should {
      "successfully update a created records agency name list" in {
        await(repository.create(multiInvitationRecord))
        await(repository.updateAgentName("uid", "chandler-bing"))

        await(repository.findByArn(Arn(arn))) shouldBe Some(
          multiInvitationRecord.copy(normalisedAgentNames = Seq("stan-lee", "chandler-bing")))
      }
    }
  }
}
