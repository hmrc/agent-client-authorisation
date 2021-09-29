package uk.gov.hmrc.agentclientauthorisation.controllers

import org.scalatest.concurrent.ScalaFutures
import uk.gov.hmrc.agentclientauthorisation.support._
import uk.gov.hmrc.agentclientauthorisation.support.UnitSpec

class BaseISpec extends
  UnitSpec with
  MongoAppAndStubs with
  AgentAuthStubs with
  ClientUserAuthStubs with
  StrideAuthStubs with
  TestDataSupport with
  DesStubs with
  CitizenDetailsStub with
  ACRStubs with
  ScalaFutures

