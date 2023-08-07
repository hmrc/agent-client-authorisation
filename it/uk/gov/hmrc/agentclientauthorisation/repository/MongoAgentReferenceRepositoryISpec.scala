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
import play.api.test.Helpers._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec
import uk.gov.hmrc.agentmtdidentifiers.model.Arn
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport

import scala.concurrent.ExecutionContext.Implicits.global

class MongoAgentReferenceRepositoryISpec
    extends UnitSpec
      with DefaultPlayMongoRepositorySupport[AgentReferenceRecord] {

  val repository = new MongoAgentReferenceRepository(mongoComponent)

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
          agentReferenceRecord("SCX39TGT", "LARN7404004").copy(normalisedAgentNames = Seq("stan-lee", "chandler-bing")))
      }
    }
  }
}
