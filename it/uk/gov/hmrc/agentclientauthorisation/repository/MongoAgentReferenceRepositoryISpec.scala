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

import org.scalatestplus.mockito.MockitoSugar
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.core.errors.DatabaseException
import uk.gov.hmrc.agentclientauthorisation.support.{MongoApp, ResetMongoBeforeTest}
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAgentReferenceRepositoryISpec
    extends UnitSpec with MongoSpecSupport with ResetMongoBeforeTest with MockitoSugar with MongoApp {

  override implicit lazy val mongoConnectorForTest: MongoConnector =
    MongoConnector(mongoUri, Some(MongoApp.failoverStrategyForTest))

  lazy val app: Application = appBuilder
    .build()

  protected def appBuilder: GuiceApplicationBuilder =
    new GuiceApplicationBuilder()
      .configure(mongoConfiguration)
      .configure(
        "invitation-status-update-scheduler.enabled" -> false,
        "mongodb-migration.enabled"                  -> false
      )

  lazy val mockReactiveMongoComponent = app.injector.instanceOf[ReactiveMongoComponent]

  lazy val repository = new MongoAgentReferenceRepository(mockReactiveMongoComponent)

  override def beforeEach() {
    super.beforeEach()
    await(repository.ensureIndexes)
    ()
  }

  "AgentReferenceRepository" when {
    def agentReferenceRecord(uid: String, arn: String) = AgentReferenceRecord(uid, Arn(arn), Seq("stan-lee"))

    "create" should {
      "successfully create a record in the repository" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004"))) shouldBe 1
        await(repository.create(agentReferenceRecord("SCX39TGA", "EARN8077593"))) shouldBe 1
        await(repository.create(agentReferenceRecord("SCX39TGB", "VARN0590057"))) shouldBe 1
      }

      "throw an error if ARN is duplicated" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004"))) shouldBe 1
        await(repository.create(agentReferenceRecord("SCX39TGA", "EARN8077593"))) shouldBe 1
        await(repository.create(agentReferenceRecord("SCX39TGB", "VARN0590057"))) shouldBe 1
        an[DatabaseException] shouldBe thrownBy {
          await(repository.create(agentReferenceRecord("SCX39TGE", "VARN0590057")))
        }
      }

      "throw an error if UID is duplicated" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004"))) shouldBe 1
        await(repository.create(agentReferenceRecord("SCX39TGA", "EARN8077593"))) shouldBe 1
        await(repository.create(agentReferenceRecord("SCX39TGB", "VARN0590057"))) shouldBe 1
        an[DatabaseException] shouldBe thrownBy {
          await(repository.create(agentReferenceRecord("SCX39TGB", "AARN5286261")))
        }
      }
    }

    "findBy" should {
      "successfully find a created record by its uid" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004")))

        await(repository.findBy("SCX39TGT")) shouldBe Some(agentReferenceRecord("SCX39TGT", "LARN7404004"))
      }
    }

    "findByArn" should {
      "successfully find a created record by its arn" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004")))

        await(repository.findByArn(Arn("LARN7404004"))) shouldBe Some(agentReferenceRecord("SCX39TGT", "LARN7404004"))
      }
    }

    "updateAgentName" should {
      "successfully update a created records agency name list" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004")))
        await(repository.updateAgentName("SCX39TGT", "chandler-bing"))

        await(repository.findByArn(Arn("LARN7404004"))) shouldBe Some(
          agentReferenceRecord("SCX39TGT", "LARN7404004").copy(normalisedAgentNames = Seq("stan-lee", "chandler-bing")))
      }
    }
  }
}
