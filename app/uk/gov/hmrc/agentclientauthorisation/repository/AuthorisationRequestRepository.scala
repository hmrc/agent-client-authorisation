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

package uk.gov.hmrc.agentclientauthorisation.repository

import reactivemongo.api.DB
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.agentclientauthorisation.model.AuthorisationRequest
import uk.gov.hmrc.domain.{AgentCode, SaUtr}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.mongo.{ReactiveRepository, Repository}

import scala.collection.Seq
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait AuthorisationRequestRepository extends Repository[AuthorisationRequest, BSONObjectID] {

  def create(agentCode: AgentCode, clientSaUtr: SaUtr): Future[AuthorisationRequest]

  def list(agentCode: AgentCode): Future[List[AuthorisationRequest]]

}

class AuthorisationRequestMongoRepository(implicit mongo: () => DB)
  extends ReactiveRepository[AuthorisationRequest, BSONObjectID]("authorisationRequests", mongo, AuthorisationRequest.mongoFormats, ReactiveMongoFormats.objectIdFormats)
    with AuthorisationRequestRepository {

  override def indexes: Seq[Index] = Seq(
    Index(Seq("agentCode" -> IndexType.Ascending))
  )

  override def create(agentCode: AgentCode, clientSaUtr: SaUtr): Future[AuthorisationRequest] = withCurrentTime { now =>
    val r = AuthorisationRequest(
      id = BSONObjectID.generate,
      agentCode = agentCode,
      clientSaUtr = clientSaUtr,
      requestDate = now
    )
    insert(r).map(_ => r)
  }

  override def list(agentCode: AgentCode): Future[List[AuthorisationRequest]] =
    find("agentCode" -> agentCode)
}
