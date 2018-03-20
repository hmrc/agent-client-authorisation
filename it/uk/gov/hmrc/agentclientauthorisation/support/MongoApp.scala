package uk.gov.hmrc.agentclientauthorisation.support

import org.scalatest.Suite
import uk.gov.hmrc.mongo.MongoSpecSupport

trait MongoApp extends MongoSpecSupport {
  me: Suite =>

  def mongoConfiguration = Map("mongodb.uri" -> mongoUri)
}