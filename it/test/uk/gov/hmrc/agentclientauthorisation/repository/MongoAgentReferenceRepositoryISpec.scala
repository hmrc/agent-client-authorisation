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

import org.mongodb.scala.MongoWriteException
import org.scalatest.time.{Millis, Seconds, Span}
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.controllers.BaseISpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

class MongoAgentReferenceRepositoryISpec extends BaseISpec with DefaultPlayMongoRepositorySupport[AgentReferenceRecord] {
  val httpClient: HttpClient = app.injector.instanceOf[HttpClient]
  val repository = new MongoAgentReferenceRepository(mongoComponent, acrConnector, lockClient)(ec, mat, appConfig)

  "AgentReferenceRepository" when {
    def agentReferenceRecord(uid: String, arn: String) = AgentReferenceRecord(uid, Arn(arn), Seq("stan-lee"))

    "create" should {
      "successfully create a record in the agentReferenceRepository" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004"))).isDefined shouldBe true
        await(repository.create(agentReferenceRecord("SCX39TGA", "EARN8077593"))).isDefined shouldBe true
        await(repository.create(agentReferenceRecord("SCX39TGB", "VARN0590057"))).isDefined shouldBe true
      }

      "throw an error if ARN is duplicated" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004"))).isDefined shouldBe true
        await(repository.create(agentReferenceRecord("SCX39TGA", "EARN8077593"))).isDefined shouldBe true
        await(repository.create(agentReferenceRecord("SCX39TGB", "VARN0590057"))).isDefined shouldBe true
        an[MongoWriteException] shouldBe thrownBy {
          await(repository.create(agentReferenceRecord("SCX39TGE", "VARN0590057")))
        }
      }

      "throw an error if UID is duplicated" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004"))).isDefined shouldBe true
        await(repository.create(agentReferenceRecord("SCX39TGA", "EARN8077593"))).isDefined shouldBe true
        await(repository.create(agentReferenceRecord("SCX39TGB", "VARN0590057"))).isDefined shouldBe true
        an[MongoWriteException] shouldBe thrownBy {
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
          agentReferenceRecord("SCX39TGT", "LARN7404004").copy(normalisedAgentNames = Seq("stan-lee", "chandler-bing"))
        )
      }
    }

    "countRemaining" should {
      "return the number of records left in the collection" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004")))
        await(repository.create(agentReferenceRecord("SCX39TPT", "LARN7404005")))
        await(repository.create(agentReferenceRecord("SCX39TPG", "LARN7404006")))

        repository.countRemaining().futureValue shouldBe 3
      }
    }
    "migrateToAcr" should {
      "iterate through all remaining items in the database and migrate them to ACR" in {
        await(repository.create(agentReferenceRecord("SCX39TGT", "LARN7404004")))
        await(repository.create(agentReferenceRecord("SCX39TPT", "LARN7404005")))
        await(repository.create(agentReferenceRecord("SCX39TPG", "LARN7404006")))
        await(repository.create(agentReferenceRecord("SCX39TGC", "LARN7404007")))
        await(repository.create(agentReferenceRecord("SCX39TPX", "LARN7404008")))
        await(repository.create(agentReferenceRecord("SCX39TPS", "LARN7404009")))

        givenMigrateAgentReferenceRecord
        givenMigrateAgentReferenceRecord
        givenMigrateAgentReferenceRecord
        givenMigrateAgentReferenceRecord
        givenMigrateAgentReferenceRecord
        givenMigrateAgentReferenceRecord

        repository.countRemaining().futureValue shouldBe 6
        val time = System.currentTimeMillis()
        repository.migrateToAcr(rate = 2)
        eventually(timeout(Span(4, Seconds)), interval(Span(100, Millis)))(repository.countRemaining().futureValue shouldBe 0)
        (System.currentTimeMillis() - time) > 2500 shouldBe true
      }
    }

  }
}
