package uk.gov.hmrc.agentclientauthorisation.support

import org.scalatest.Suite
import uk.gov.hmrc.mongo.MongoSpecSupport

trait MongoApp extends MongoSpecSupport {
  me: Suite =>

  protected def mongoConfiguration = Map("mongodb.uri" -> mongoUri)
}
