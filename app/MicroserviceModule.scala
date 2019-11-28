/*
 * Copyright 2019 HM Revenue & Customs
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

import com.google.inject.name.Names
import play.api.{Configuration, Environment}
import play.api.inject.{Binding, Module}
import uk.gov.hmrc.agentclientauthorisation.repository.{InvitationsRepository, InvitationsRepositoryImpl, MongoScheduleRepository, ScheduleRepository}
import uk.gov.hmrc.agentclientauthorisation.service.{AgentCacheProvider, InvitationsStatusUpdateScheduler, RepositoryMigrationService}

import scala.collection.mutable.ListBuffer
/*
 * Copyright 2019 HM Revenue & Customs
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

class MicroserviceModule extends Module {

  def bindings(environment: Environment, configuration: Configuration) = {

    val optionalBindings: Seq[Binding[_]] = {

      val list = ListBuffer.empty[Binding[_]]
      if (configuration.underlying.getBoolean("mongodb-migration.enabled")) {
        list.append(bind(classOf[RepositoryMigrationService]).toSelf.eagerly())
      }

      if (configuration.underlying.getBoolean("invitation-status-update-scheduler.enabled")) {
        list.append(bind(classOf[InvitationsStatusUpdateScheduler]).toSelf.eagerly())
      }

      list
    }
    Seq(
      bind(classOf[RepositoryMigrationService]).toSelf.eagerly(),
      bind(classOf[InvitationsStatusUpdateScheduler]).toSelf.eagerly(),
      bind(classOf[ScheduleRepository]).to(classOf[MongoScheduleRepository]),
      bind(classOf[AgentCacheProvider]).toSelf.eagerly(),
      bind(classOf[InvitationsRepository]).to(classOf[InvitationsRepositoryImpl])
    ) ++ optionalBindings
  }
}
