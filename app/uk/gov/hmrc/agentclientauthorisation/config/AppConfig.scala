/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.agentclientauthorisation.config

import java.net.URLDecoder
import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration.Duration

@Singleton
class AppConfig @Inject()(servicesConfig: ServicesConfig) {

  val appName = "agent-client-authorisation"

  private def getConf(key: String) =
    servicesConfig.getConfString(key, throw new RuntimeException(s"config $key not found"))
  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  val authBaseUrl = baseUrl("auth")

  val desBaseUrl = baseUrl("des")
  val desEnvironment = getConf("des.environment")
  val desAuthToken = getConf("des.authorization-token")

  val serviceLocatorBaseUrl = baseUrl("service-locator")

  val relationshipsBaseUrl = baseUrl("relationships")

  val afiRelationshipsBaseUrl = baseUrl("afi-relationships")

  val citizenDetailsBaseUrl = baseUrl("citizen-details")

  val niExemptionRegistrationBaseUrl = baseUrl("ni-exemption-registration")

  val emailBaseUrl = baseUrl("email")

  val agentInvitationsFrontendExternalUrl = getConf("agent-invitations-frontend.external-url")

  val oldStrideEnrolment = URLDecoder.decode(servicesConfig.getString("old.auth.stride.enrolment"), "utf-8")
  val newStrideEnrolment = servicesConfig.getString("new.auth.stride.enrolment")

  val invitationUpdateStatusInterval: Int = servicesConfig.getInt("invitation-status-update-scheduler.interval")
  val invitationStatusUpdateEnabled: Boolean = servicesConfig.getBoolean("invitation-status-update-scheduler.enabled")

  val invitationExpiringDuration: Duration = servicesConfig.getDuration("invitation.expiryDuration")

  val agentCacheSize = servicesConfig.getInt("agent.cache.size")
  val agentCacheExpires = servicesConfig.getDuration("agent.cache.expires")
  val agentCacheEnabled = servicesConfig.getBoolean("agent.cache.enabled")

}
