/*
 * Copyright 2023 HM Revenue & Customs
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

import uk.gov.hmrc.agentclientauthorisation.model.BasicAuthentication
import uk.gov.hmrc.agentservice.HodApiConfig
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.net.URLDecoder
import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration

@Singleton
class AppConfig @Inject()(servicesConfig: ServicesConfig) {

  val appName = "agent-client-authorisation"

  private def getConf(key: String) =
    servicesConfig.getConfString(key, throw new RuntimeException(s"config $key not found"))
  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  val authBaseUrl: String = baseUrl("auth")

  val desBaseUrl: String = baseUrl("des")
  val desEnvironment: String = getConf("des.environment")
  val desAuthToken: String = getConf("des.authorization-token")

  val ifPlatformBaseUrl: String = baseUrl("if")
  val ifEnvironment: String = getConf("if.environment")
  val ifAuthTokenAPI1171: String = getConf("if.authorization-token.API1171")
  val ifAuthTokenAPI1712: String = getConf("if.authorization-token.API1712")
  val ifAuthTokenAPI1495: String = getConf("if.authorization-token.API1495")
  val ifAuthTokenAPI2143: String = getConf("if.authorization-token.API2143")

  val eisBaseUrl: String = baseUrl("eis")
  val eisEnvironment: String = getConf("eis.environment")
  val eisAuthToken: String = getConf("eis.authorization-token")

  val relationshipsBaseUrl: String = baseUrl("relationships")

  val afiRelationshipsBaseUrl: String = baseUrl("afi-relationships")

  val citizenDetailsBaseUrl: String = baseUrl("citizen-details")

  val enrolmentStoreProxyUrl: String = baseUrl("enrolment-store-proxy")

  val emailBaseUrl: String = baseUrl("email")

  val agentInvitationsFrontendExternalUrl: String = getConf("agent-invitations-frontend.external-url")

  val oldStrideEnrolment: String = URLDecoder.decode(servicesConfig.getString("old.auth.stride.enrolment"), "utf-8")
  val newStrideEnrolment: String = servicesConfig.getString("new.auth.stride.enrolment")
  val altStrideEnrolment: String = servicesConfig.getString("alt.auth.stride.enrolment")
  val terminationStrideEnrolment: String = servicesConfig.getString("termination.stride.enrolment")

  val invitationUpdateStatusInterval: Int = servicesConfig.getInt("invitation-status-update-scheduler.interval")
  val invitationStatusUpdateEnabled: Boolean = servicesConfig.getBoolean("invitation-status-update-scheduler.enabled")

  val invitationExpiringDuration: Duration = servicesConfig.getDuration("invitation.expiryDuration")

  val removePersonalInfoSchedulerEnabled: Boolean = servicesConfig.getBoolean("remove-personal-info-scheduler.enabled")
  val removePersonalInfoScheduleInterval: Int = servicesConfig.getInt("remove-personal-info-scheduler.interval")
  val removePersonalInfoExpiryDuration: Duration = servicesConfig.getDuration("remove-personal-info-scheduler.expiryDuration")

  val agentCacheSize: Int = servicesConfig.getInt("agent.cache.size")
  val agentCacheExpires: Duration = servicesConfig.getDuration("agent.cache.expires")
  val agentCacheEnabled: Boolean = servicesConfig.getBoolean("agent.cache.enabled")

  def expectedAuth: BasicAuthentication = {
    val username = servicesConfig.getString("agent-termination.username")
    val password = servicesConfig.getString("agent-termination.password")

    BasicAuthentication(username, password)
  }

  val platformAnalyticsBaseUrl: String = baseUrl("platform-analytics")
  val gaTrackingId: String = servicesConfig.getString("google-analytics.token")
  val gaBatchSize: Int = servicesConfig.getInt("google-analytics.batchSize")
  val gaClientTypeIndex: Int = servicesConfig.getInt("google-analytics.clientTypeIndex")
  val gaInvitationIdIndex: Int = servicesConfig.getInt("google-analytics.invitationIdIndex")
  val gaOriginIndex: Int = servicesConfig.getInt("google-analytics.originIndex")
  val gaAltItsaIndex: Int = servicesConfig.getInt("google-analytics.altItsaIndex")

  val sendEmailPriorToExpireDays: Int = servicesConfig.getInt("invitation-about-to-expire-warning-email.daysPrior")
  val desIFEnabled: Boolean = servicesConfig.getBoolean("des-if.enabled")
  val altItsaEnabled: Boolean = servicesConfig.getBoolean("alt-itsa.enabled")
  val altItsaExpiryDays: Int = servicesConfig.getInt("alt-itsa-expiry-days")
  val altItsaExpiryEnable: Boolean = servicesConfig.getBoolean("alt-itsa-expiry-enable")

  lazy val maxCallsPerSecondBusinessNames: Double = servicesConfig.getString("rate-limiter.business-names.max-calls-per-second").toDouble

  val desConfigBundle = HodApiConfig(desBaseUrl, Seq("Authorization"        -> desAuthToken, "Environment"       -> desEnvironment))
  val ifApi1171Bundle = HodApiConfig(ifPlatformBaseUrl, Seq("Authorization" -> ifAuthTokenAPI1171, "Environment" -> ifEnvironment))

}
