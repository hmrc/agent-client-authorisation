package uk.gov.hmrc.agentclientauthorisation.config

import javax.inject.Inject
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppConfig @Inject()(servicesConfig: ServicesConfig) {


  private def getConf(key: String) = servicesConfig.getString(key)
  private def baseUrl(key: String) = servicesConfig.baseUrl(key)

  val authBaseUrl: String = baseUrl("auth")
  val relationshipsBaseUrl: String = baseUrl("relationships")
  val afiRelationshipsBaseUrl: String = baseUrl("afi-relationships")
  val agentServicesAccountBaseUrl: String = baseUrl("agent-services-account")
  val desBaseUrl: String = baseUrl("des")
  val citizenDetailsBaseUrl: String = baseUrl("citizen-details")
  val niExemptionRegistrationBaseUrl: String = baseUrl("ni-exemption-registration")
  val emailBaseUrl: String = baseUrl("email")

  val desEnv: String = getConf("microservice.services.des.environment")
  val desKey: String = getConf("microservice.services.des.authorization-token")


  bindPropertyWithFun("invitation.expiryDuration", _.replace("_", " "))
  bindProperty2param("agent-invitations-frontend.external-url", "agent-invitations-frontend.external-url")
  bindIntegerProperty("invitation-status-update-scheduler.interval")
  bindBooleanProperty("invitation-status-update-scheduler.enabled")
  bindBooleanProperty("mongodb-migration.enabled")

}
