package uk.gov.hmrc.agentclientauthorisation.controllers

import uk.gov.hmrc.agentclientauthorisation.support._

class BaseISpec extends
  UnitSpec with
  MongoAppAndStubs with
  AgentAuthStubs with
  ClientUserAuthStubs with
  StrideAuthStubs with
  TestDataSupport with
  DesStubs with
  CitizenDetailsStub with
  ACRStubs

