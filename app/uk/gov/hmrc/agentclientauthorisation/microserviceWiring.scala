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

package uk.gov.hmrc.agentclientauthorisation

import java.net.URL

import play.api.mvc.Controller
import play.modules.reactivemongo.ReactiveMongoPlugin
import uk.gov.hmrc.agentclientauthorisation.connectors.{AgenciesFakeConnector, RelationshipsConnector}
import uk.gov.hmrc.agentclientauthorisation.controllers.{ClientInvitationsController, AgencyInvitationsController, WhitelistController}
import uk.gov.hmrc.agentclientauthorisation.repository.InvitationsMongoRepository
import uk.gov.hmrc.agentclientauthorisation.service.{InvitationsService, PostcodeService}
import uk.gov.hmrc.api.connector.ServiceLocatorConnector
import uk.gov.hmrc.mongo.MongoConnector
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.audit.http.config.LoadAuditingConfig
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.auth.microservice.connectors.AuthConnector
import uk.gov.hmrc.play.config.{AppName, RunMode, ServicesConfig}
import uk.gov.hmrc.play.http.hooks.HttpHook
import uk.gov.hmrc.play.http.ws._

object WSHttp extends WSGet with WSPut with WSPost with WSDelete with WSPatch with AppName with HttpAuditing {
  override val hooks: Seq[HttpHook] = Seq(AuditingHook)
  override val auditConnector = MicroserviceAuditConnector
}

object MicroserviceAuditConnector extends AuditConnector with RunMode {
  override lazy val auditingConfig = LoadAuditingConfig(s"auditing")
}

object MicroserviceAuthConnector extends AuthConnector with ServicesConfig {
  override val authBaseUrl = baseUrl("auth")
}

trait LazyMongoDbConnection {
  import play.api.Play.current

  lazy val mongoConnector: MongoConnector = ReactiveMongoPlugin.mongoConnector

  implicit lazy val db = mongoConnector.db
}

trait ServiceRegistry extends ServicesConfig with LazyMongoDbConnection {

  // Instantiate services here
  lazy val relationshipsConnector = new RelationshipsConnector(new URL(baseUrl("relationships")), WSHttp)
  lazy val invitationsService = new InvitationsService(new InvitationsMongoRepository, relationshipsConnector)
  lazy val postcodeService = new PostcodeService
  lazy val authConnector = new uk.gov.hmrc.agentclientauthorisation.connectors.AuthConnector(new URL(baseUrl("auth")), WSHttp)
  lazy val slConnector = ServiceLocatorConnector(WSHttp)
  lazy val agenciesFakeConnector = new AgenciesFakeConnector(new URL(baseUrl("agencies-fake")), WSHttp)
}

trait ControllerRegistry {
  registry: ServiceRegistry =>

  private lazy val controllers = Map[Class[_], Controller](
    classOf[AgencyInvitationsController] -> new AgencyInvitationsController(invitationsService, postcodeService, authConnector, agenciesFakeConnector),
    classOf[ClientInvitationsController] -> new ClientInvitationsController(invitationsService, authConnector, agenciesFakeConnector),
    classOf[WhitelistController] -> new WhitelistController()
  )

  def getController[A](controllerClass: Class[A]) : A = controllers(controllerClass).asInstanceOf[A]
}
