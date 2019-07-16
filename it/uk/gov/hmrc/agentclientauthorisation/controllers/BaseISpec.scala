package uk.gov.hmrc.agentclientauthorisation.controllers

import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.play.test.UnitSpec

class BaseISpec extends UnitSpec with MongoAppAndStubs with AgentAuthStubs with ClientUserAuthStubs with StrideAuthStubs with TestDataSupport with DesStubs with AgentServicesAccountStub with CitizenDetailsStub with ACRStubs
